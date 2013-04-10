package nl.willemburgers.android.z_droid;

import java.io.*;
import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.Queue;

import nl.willemburgers.android.z_droid.Defines.*;
import nl.willemburgers.android.z_droid.Node.QueryStage;

import com.hoho.android.usbserial.util.HexDump;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.util.Log;

import de.hallenbeck.indiserver.communication_drivers.PL2303callback;
import de.hallenbeck.indiserver.communication_drivers.PL2303driver;
import de.hallenbeck.indiserver.communication_drivers.PL2303driver.*;

public final class ZWaveController implements PL2303callback {
	private final String TAG = "Z-Droid";
	private Context AppContext;
	private PL2303driver pl2303;
	private boolean connected = false;
	private boolean reading = false;
	
	private Mutex queueMutex;
	private Mutex m_nodeMutex;
	private Queue<Msg> sendQueue;
	private Event sendEvent;
	
	private boolean m_waitingForAck;
	private int m_ACKWaiting = 0;
	private int m_badChecksum = 0;
	private int m_readCnt = 0;
	
	private byte m_expectedReply = 0x00;
	private byte m_expectedCallbackId = 0x00;
	private byte m_expectedCommandClassId = 0x00;
	
	private long m_homeId;
	
	private String m_libraryVersion = "";
	private String m_libraryTypeName = "";
	private byte m_libraryType = 0;
	
	private int m_serialAPIVersion[] = new int[2];
	private int m_manufacturerId = 0;
	private int m_productType = 0;
	private int m_productId = 0;
	private byte[] m_apiMask = new byte[32];
	
	private boolean m_init = false;
	private byte m_initVersion;
	private byte m_initCaps = 0x00;
	
	private byte m_controllerCaps;
	
	private byte m_nodeId;
	private Node[] m_nodes = new Node[256];
	
	private static final String[] c_libraryTypeNames =
		{
		"Unknown",
		"Static Controller",
		"Controller",
		"Enhanced Slave",
		"Slave",
		"Installer",
		"Routing Slave",
		"Bridge Controller",
		"Device Under Test"
	};
	
	public enum MsgQueue
	{
		MsgQueue_Command,
		MsgQueue_NoOp,
		MsgQueue_Controller,
		MsgQueue_WakeUp,
		MsgQueue_Send,
		MsgQueue_Query,
		MsgQueue_Poll,
		MsgQueue_Count		// Number of message queues
	};

	public ZWaveController(Context context, UsbDevice device) {
		AppContext = context;
		pl2303 = new PL2303driver(AppContext, device, this);
		
		try{
			if(pl2303.open()){
				if (pl2303.initalize()){
					Log.d(TAG, "Device successfully initialized");
					onInitSuccess();
				} else {
					Log.d(TAG, "Device initialization failed");
					onInitFailed("Device initialization failed");
					close();
				}
			}
		}
		catch (Exception e){
        	e.printStackTrace();
        }
	}

	public void close() {
		Log.i(TAG, "Closing connection");
		connected = false;
		pl2303.close();
	}
	
	public void SendMsg(Msg msg, MsgQueue _queue){
		msg.Finalize();
		Log.d(TAG,"Queueing message: " + msg.toString());
		synchronized(queueMutex){
			sendQueue.add(msg);
		}
		UpdateEvents();
	}
	
	private Node GetNode (byte _nodeId)
	{
		Node node;
		synchronized(m_nodeMutex)
		{
			node = m_nodes[_nodeId];
		}
		return node;
	}
	
	private void InitNode (byte _nodeId)
	{
		// Delete any existing node and replace it with a new one
		synchronized(m_nodeMutex)
		{
			if( m_nodes[_nodeId] != null )
			{
				// Remove the original node
				/*Notification* notification = new Notification( Notification::Type_NodeRemoved );
				notification->SetHomeAndNodeIds( m_homeId, _nodeId );
				QueueNotification( notification );*/
			}
	
			// Add the new node
			m_nodes[_nodeId] = new Node(this, m_homeId, _nodeId );
		}

		/*Notification* notification = new Notification( Notification::Type_NodeAdded );
		notification->SetHomeAndNodeIds( m_homeId, _nodeId );
		QueueNotification( notification );*/

		// Request the node info
		//m_nodes[_nodeId]->SetQueryStage( Node::QueryStage_ProtocolInfo );
	}
	
	private boolean IsBridgeController(){ return (m_libraryType == 0x07); }
	
	public byte GetNodeId(){return m_nodeId;}
	
	private boolean IsAPICallSupported(final byte _apinum)
	{return (( m_apiMask[( _apinum - 1 ) >> 3] & ( 1 << (( _apinum - 1 ) & (byte)0x07 ))) != 0 ); }


	
	private void RemoveMsg(){
		synchronized(queueMutex){
			sendQueue.poll();
		}
	}
	
	private void TriggerResend(){
		synchronized(queueMutex){
			Log.d(TAG, "Resend");
			Msg msg = sendQueue.peek();
			msg.SetSendAttempts( (byte) 0 );
	
			m_waitingForAck = false;
			m_expectedCallbackId = 0;
			m_expectedReply = 0;
			m_expectedCommandClassId = 0;
	
			Log.d(TAG, "Resend set event");
			sendEvent.Set();
		}
	}
	
	private void UpdateEvents(){
		if( m_waitingForAck || (m_expectedCallbackId != 0x00) || (m_expectedReply != 0x00) )
		{
			// Waiting for ack, callback or a specific message type, so we can't transmit anything yet.
			sendEvent.Reset();
		}
		else
		{
			// Allow transmissions to occur
			if((!sendQueue.isEmpty()))// || ( !m_infoQueue.empty() ) )
			{
				sendEvent.Set();
			}
		}
	}
	
	private int ByteToInt(byte b){
		return (int)(b&0xFF);
	}

	public void onInitSuccess() {
		queueMutex = new Mutex();
		m_nodeMutex = new Mutex();
		sendQueue = new LinkedList<Msg>();
		sendEvent = new Event();
		
		m_nodeId = -1;
		m_waitingForAck = false;
		
		try {
			pl2303.setup(BaudRate.B115200,DataBits.D8, StopBits.S1, Parity.NONE, FlowControl.OFF);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		pl2303.setDTR(true);
		pl2303.setRTS(true);
		connected = true;
		
		ThreadGroup tg = new ThreadGroup(TAG);
		Thread readThread = new ReadThread(tg, pl2303);
		Thread writeThread = new SendThread(tg, pl2303);
		
		readThread.start();
		
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			pl2303.getOutputStream().write(Defines.NAK);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		Msg msg;
		
		Log.d(TAG, "Get Version");
        msg = new Msg("FUNC_ID_ZW_GET_VERSION", (byte)0xff, Defines.REQUEST, Defines.FUNC_ID_ZW_GET_VERSION, false);
        this.SendMsg(msg);
        
        Log.d(TAG, "Get Home and Node IDs");
        msg = new Msg( "FUNC_ID_ZW_MEMORY_GET_ID", (byte) 0xff, Defines.REQUEST, Defines.FUNC_ID_ZW_MEMORY_GET_ID, false );
        this.SendMsg(msg);
        
        Log.d(TAG, "Get Controller Capabilities");
    	msg = new Msg( "FUNC_ID_ZW_GET_CONTROLLER_CAPABILITIES", (byte) 0xff, Defines.REQUEST, Defines.FUNC_ID_ZW_GET_CONTROLLER_CAPABILITIES, false );
    	this.SendMsg(msg);
    	
    	Log.d(TAG, "Get Serial API Capabilities");
    	msg = new Msg( "FUNC_ID_SERIAL_API_GET_CAPABILITIES", (byte) 0xff, Defines.REQUEST, Defines.FUNC_ID_SERIAL_API_GET_CAPABILITIES, false );
    	this.SendMsg(msg);
    	
    	writeThread.start();
	}

	public void onInitFailed(String reason) {
		Log.d(TAG, "init failed: " + reason);
		this.close();
		
	}

	public void onRI(boolean state) {
		// TODO Auto-generated method stub
		
	}

	public void onDCD(boolean state) {
		// TODO Auto-generated method stub
		
	}

	public void onDSR(boolean state) {
		if (state) {
			Log.i("PL2303device","Modem ready");
			if (!connected) {
				connected = true;
			}
		} else {
			Log.i("PL2303device","Modem off");
			if (connected) {
				connected = false;
			}
		}
	}

	public void onCTS(boolean state) {
		// TODO Auto-generated method stub
		
	}
	
	private class ReadThread extends Thread{
		private OutputStream out;
		private InputStream in;
		public ReadThread(ThreadGroup tg, PL2303driver _pl2303){
			super(tg, "readThread");
			this.out = _pl2303.getOutputStream();
			this.in = _pl2303.getInputStream();
		}
		public void run(){
			while(connected)
			{
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				// Consume any available messages
				while(ReadMsg());
			}
		}
		
		private boolean ReadMsg(){
			boolean error = false;
			if(!connected)
				return false;
			try {
				byte[] buffer = new byte[256];
				
				int actuallyRead = in.read(buffer, 0, 1);
				if(buffer[0]!=0x00)
				{
					actuallyRead = 1;
				}
				if(actuallyRead <= 0)
				{
					return false;
				}
				switch( buffer[0] )
				{
				case Defines.SOF:
					reading = true;
					if( m_waitingForAck ){
						Log.d(TAG, "Unsolicited message received while waiting for ACK.");
						m_ACKWaiting++;
					}
					int length = -1;
					int retry = 50;
					while (length <= 1 && retry > 0){
						if(!connected){
							return false;
						}
						actuallyRead = in.read(buffer,1,1);
						if(actuallyRead <= 0)
						{
							--retry;
							continue;
						}
						length = ByteToInt(buffer[1]);
						--retry;
					}
					Log.d(TAG, "length, retry : " + length + " " + retry);
					int lengthRead = 0;
					retry = 50;
					while (lengthRead < length && retry > 0){
						if(!connected){
							return false;
						}
						int offset = 2 + lengthRead;
						actuallyRead = in.read(buffer,offset,length-lengthRead);
						if(actuallyRead <= 0){
							--retry;
							continue;
						}
						lengthRead += actuallyRead;
						//Log.d(TAG, "Read " + lengthRead + " byte(s)");
					}
					
					reading = false;
					
					length+=2;
					
					// Verify checksum
					byte checksum = (byte) 0xff;
					for( int i=1; i<(length-1); ++i )
					{
						checksum ^= buffer[i];
					}

					if( buffer[length-1] == checksum )
					{
						// Checksum correct - send ACK
						out.write(Defines.ACK);
						m_readCnt++;
						Log.d(TAG,"Checksum correct - sending ACK");

						// Process the received message
						byte[] copy = new byte[length-3];
						System.arraycopy(buffer, 2, copy, 0, length-3);
						processMsg(copy);
					}
					else
					{
						Log.d(TAG, "message received: " + HexDump.toHexString(buffer));
						m_badChecksum++;
						out.write(Defines.NAK);
						Log.e(TAG,"WARNING: Checksum incorrect - sending NAK");
						error = true;
					}
					break;
				case Defines.CAN:
					Log.d(TAG, "CAN, triggering resend");
					TriggerResend();
					break;
				case Defines.NAK:
					Log.d(TAG, "NAK, triggering resend");
					TriggerResend();
					break;
				case Defines.ACK:
					Log.d(TAG, "ACK received");
					m_waitingForAck = false;
					
					if( ( 0 == m_expectedCallbackId ) && ( 0 == m_expectedReply ) )
					{
						// Remove the message from the queue, now that it has been acknowledged.
						RemoveMsg();
						UpdateEvents();
					}
					break;
				default:
					Log.e(TAG, "Out Of Frame flow.");
					Log.d(TAG, "buffer: " + HexDump.toHexString(buffer));
					error = true;
					//out.write(Defines.NAK);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			return !error;
		}
		
		private void processMsg(byte[] data){
			Log.d(TAG, "msg: " + HexDump.toHexString(data));
			
			boolean handleCallback = true;
			
			if(data[0] == Defines.RESPONSE)
			{
				switch (data[1])
				{
					case Defines.FUNC_ID_ZW_GET_VERSION:
						HandleGetVersionResponse(data);
						break;
					case Defines.FUNC_ID_ZW_GET_RANDOM:
						HandleGetRandomResponse(data);
						break;
					case Defines.FUNC_ID_ZW_MEMORY_GET_ID:
						HandleMemoryGetIdResponse(data);
						break;
					case Defines.FUNC_ID_ZW_GET_CONTROLLER_CAPABILITIES:
						HandleGetControllerCapabilitiesResponse(data);
						break;
					case Defines.FUNC_ID_SERIAL_API_GET_CAPABILITIES:
						HandleGetSerialAPICapabilitiesResponse(data);
						break;
					case Defines.FUNC_ID_ZW_ENABLE_SUC:
						//HandleEnableSUCResponse(data);
						break;
					case Defines.FUNC_ID_SERIAL_API_GET_INIT_DATA:
						HandleSerialAPIGetInitDataResponse(data);
						break;
					case Defines.FUNC_ID_SERIAL_API_SET_TIMEOUTS:
						HandleSerialApiSetTimeoutsResponse(data);
						break;
				}
			}
			if(handleCallback){
				Log.d(TAG, "ProcessMsg: m_expectedCallbackId="+HexDump.toHexString(m_expectedCallbackId)+" msg[2]="+HexDump.toHexString(data[2])+" m_expectedReply="+HexDump.toHexString(m_expectedReply)+" msg[1]="+HexDump.toHexString(data[1]));// m_expectedCommandClassId=%x _data[5]=%x", m_expectedCallbackId, _data[2], m_expectedReply, _data[1], m_expectedCommandClassId, _data[5]);
				if(m_expectedCallbackId != 0x00 || m_expectedReply != 0x00)
				{
					if(m_expectedCallbackId != 0x00)
					{
						if(m_expectedCallbackId == data[2])
						{
							Log.d(TAG, "Expected callbackId was received");
							m_expectedCallbackId = 0;
						}
					}
					if(m_expectedReply != 0x00)
					{
						if(m_expectedReply == data[1])
						{
							if((m_expectedCommandClassId != 0x00) && (m_expectedReply == Defines.FUNC_ID_APPLICATION_COMMAND_HANDLER))
							{
								if(m_expectedCommandClassId == data[5])
								{
									Log.d(TAG, "Expected reply and command class was received");
									m_expectedReply = 0;
									m_expectedCommandClassId = 0;
								}
							}
							else
							{
								Log.d(TAG, "Expected reply was received");
								m_expectedReply = 0;
							}
						}
					}

					if(!((m_expectedCallbackId != 0x00) || (m_expectedReply != 0x00)))
					{
						Log.d(TAG, "Message transaction complete");
						if( data[1] == Defines.FUNC_ID_ZW_SEND_DATA)
						{
							/*Msg msg = m_sendQueue.peek();
							byte n = msg->GetTargetNodeId();
							byte clsid = msg->GetExpectedCommandClassId();
							Node node = m_nodes[n];
							if( node != NULL )
							{
								CommandClass *cc = node->GetCommandClass(clsid);
							}*/
						}
						RemoveMsg();
					}
				}
			}
			UpdateEvents();
		}
		
		private void HandleGetVersionResponse(byte[] msg){
			int i = 2;
			m_libraryVersion = "";
			while(msg[i]!= 0x00)
			{
				m_libraryVersion += (char) msg[i];
				++i;
			}
			m_libraryType = msg[m_libraryVersion.length()+3];
			if(m_libraryType<9){
				m_libraryTypeName = c_libraryTypeNames[m_libraryType];
			}
			Log.i(TAG,"Received reply to FUNC_ID_ZW_GET_VERSION:");
			Log.i(TAG,m_libraryTypeName + " library, version " + m_libraryVersion);
		}
		
		private void HandleGetRandomResponse(byte[] data){
			Log.i( TAG, "Received reply to FUNC_ID_ZW_GET_RANDOM:" + ((data[2] != 0x00) ? "true" : "false"));
		}
		
		private void HandleGetControllerCapabilitiesResponse(byte[] msg){
			m_controllerCaps = msg[2];
			
			Log.i(TAG, "Received reply to FUNC_ID_ZW_GET_CONTROLLER_CAPABILITIES:");

			String str = "";
			if((m_controllerCaps & ControllerDefines.ControllerCaps_SIS) != 0x00)
			{
				Log.i(TAG, " There is a SUC ID Server (SIS) in this network.");
				str += " The PC controller is an inclusion ";
				str+= ((m_controllerCaps & ControllerDefines.ControllerCaps_SUC) != 0x00) ? "static update controller (SUC)" : "controller";
				str+= ((m_controllerCaps & ControllerDefines.ControllerCaps_OnOtherNetwork) != 0x00) ? " which is using a Home ID from another network" : "";
				str+= ((m_controllerCaps & ControllerDefines.ControllerCaps_RealPrimary) != 0x00) ? " and was the original primary before the SIS was added." : ".";
				Log.i(TAG, str);
			}
			else
			{
				Log.i(TAG, " There is no SUC ID Server (SIS) in this network.");
				str+= " The PC controller is a ";
				str+= ((m_controllerCaps & ControllerDefines.ControllerCaps_Secondary) != 0x00) ? "secondary" : "primary";
				str+= ((m_controllerCaps & ControllerDefines.ControllerCaps_SUC) != 0x00) ? " static update controller (SUC)" : " controller";
				str+= ((m_controllerCaps & ControllerDefines.ControllerCaps_OnOtherNetwork) != 0x00) ? " which is using a Home ID from another network." : ".";
				Log.i(TAG, str);
			}
		}
		
		private void HandleGetSerialAPICapabilitiesResponse(byte[] data) {
			Log.i(TAG, "Received reply to FUNC_ID_SERIAL_API_GET_CAPABILITIES" );
			Log.i(TAG, "Serial API Version: " + ByteToInt(data[2]) +"."+ ByteToInt(data[3]));
			Log.i(TAG, "Manufacturer ID: 0x" + HexDump.toHexString(data[4]) + HexDump.toHexString(data[5]));
			Log.i(TAG, "Product Type: 0x" + HexDump.toHexString(data[6]) + HexDump.toHexString(data[7]));
			Log.i(TAG, "Product ID: 0x" + HexDump.toHexString(data[8]) + HexDump.toHexString(data[9]));

			// msg[10] to msg[41] are a 256-bit bitmask with one bit set for
			// each FUNC_ID_ method supported by the controller.
			// Bit 0 is FUNC_ID_ 1. So FUNC_ID_SERIAL_API_GET_CAPABILITIES (0x07) will be bit 6 of the first byte.
			m_serialAPIVersion[0] = ByteToInt(data[2]);
			m_serialAPIVersion[1] = ByteToInt(data[3]);
			m_manufacturerId = (ByteToInt(data[4])<<8) | ByteToInt(data[5]);
			m_productType = (ByteToInt(data[6])<<8) | ByteToInt(data[7]);
			m_productId = (ByteToInt(data[8])<<8) | ByteToInt(data[9]);
			System.arraycopy(data, 10, m_apiMask, 0, m_apiMask.length);

			if( IsBridgeController() ) {
				SendMsg( new Msg( "FUNC_ID_ZW_GET_VIRTUAL_NODES", (byte)0xff, Defines.REQUEST, Defines.FUNC_ID_ZW_GET_VIRTUAL_NODES, false ));//, MsgQueue_Command);
			}
			else if(IsAPICallSupported(Defines.FUNC_ID_ZW_GET_RANDOM)) {
				Msg msg = new Msg("FUNC_ID_ZW_GET_RANDOM", (byte)0xff, Defines.REQUEST, Defines.FUNC_ID_ZW_GET_RANDOM, false );
				msg.Append((byte) 32); // 32 bytes
				SendMsg(msg);//, MsgQueue_Command );
			}
			SendMsg( new Msg("FUNC_ID_SERIAL_API_GET_INIT_DATA", (byte)0xff, Defines.REQUEST, Defines.FUNC_ID_SERIAL_API_GET_INIT_DATA, false ));//, MsgQueue_Command);
			if( !IsBridgeController() )
			{
				Msg msg = new Msg("FUNC_ID_SERIAL_API_SET_TIMEOUTS", (byte)0xff, Defines.REQUEST, Defines.FUNC_ID_SERIAL_API_SET_TIMEOUTS, false );
				msg.Append( (byte) (Defines.ACK_TIMEOUT/10) );
				msg.Append( (byte) (Defines.BYTE_TIMEOUT/10) );
				SendMsg(msg);//, MsgQueue_Command );
			}
			Msg msg = new Msg( "FUNC_ID_SERIAL_API_APPL_NODE_INFORMATION", (byte)0xff, Defines.REQUEST, Defines.FUNC_ID_SERIAL_API_APPL_NODE_INFORMATION, false, false );
			msg.Append( Defines.APPLICATION_NODEINFO_LISTENING );
			msg.Append( (byte) 0x02 ); // Generic Static Controller
			msg.Append( (byte) 0x01 ); // Specific Static PC Controller
			msg.Append( (byte) 0x01 );
			msg.Append( (byte) 0x2b ); // Scene Activation
			SendMsg(msg);//, MsgQueue_Command );
		}
		
		private void HandleMemoryGetIdResponse(byte[] msg) {
			byte[] homeId = new byte[4];
			System.arraycopy(msg, 2, homeId, 0, 4);
			Log.i(TAG, "Received reply to FUNC_ID_ZW_MEMORY_GET_ID. Home ID = 0x"+HexDump.toHexString(homeId)+".  Our node ID = "+msg[6]);
			m_homeId = ByteToInt(msg[2])<<24 | ByteToInt(msg[3])<<16 | ByteToInt(msg[4])<<8 | ByteToInt(msg[5]);
			m_nodeId = msg[6];
			//m_controllerReplication = static_cast<ControllerReplication*>(ControllerReplication::Create( m_homeId, m_nodeId ));
		}
		
		private void HandleSerialAPIGetInitDataResponse(byte[] msg) {
			int i;
			DecimalFormat df = new DecimalFormat("000"); // Set your desired format here.
			
			if(!m_init)
			{
				// Mark the driver as ready (we have to do this first or
				// all the code handling notifications will go awry).
				//Manager::Get()->SetDriverReady( this, true );
	
				// Read the config file first, to get the last known state
				//ReadConfig();
			}
			else
			{
				// Notify the user that all node and value information has been deleted
				// We need to wait to do this here so we have new information to report.
				//Notification* notification = new Notification( Notification::Type_DriverReset );
				//notification->SetHomeAndNodeIds( m_homeId, 0 );
				//QueueNotification( notification );
			}
			
			Log.i(TAG, "Received reply to FUNC_ID_SERIAL_API_GET_INIT_DATA:");
			m_initVersion = msg[2];
			m_initCaps = msg[3];

			if( msg[4] == Defines.NUM_NODE_BITFIELD_BYTES )
			{
				for( i=0; i<Defines.NUM_NODE_BITFIELD_BYTES; ++i)
				{
					for( int j=0; j<8; ++j )
					{
						byte nodeId = (byte) ((i*8)+j+1);
						if((msg[i+5] & (0x01 << j)) != 0x00)
						{
							Node node = GetNode( nodeId );
							if( node != null )
							{
								Log.i(TAG, " Node "+df.format(ByteToInt(nodeId))+" - Known");
								/*if( !m_init )
								{
									// The node was read in from the config, so we
									// only need to get its current state
									node->RequestState( CommandClass::RequestFlag_Session | CommandClass::RequestFlag_Dynamic );
								}

								ReleaseNodes();*/
							}
							else
							{
								
								// This node is new
								Log.i(TAG, " Node "+df.format(ByteToInt(nodeId))+" - New");

								// Create the node and request its info
								InitNode( nodeId );
							}
						}
						else
						{
							/*if( GetNode(nodeId) )
							{
								Log.i(TAG, " Node "+df.format(ByteToInt(nodeId))+" - Removed");

								// This node no longer exists in the Z-Wave network
								Notification* notification = new Notification( Notification::Type_NodeRemoved );
								notification->SetHomeAndNodeIds( m_homeId, nodeId );
								QueueNotification( notification );

								m_nodes[nodeId] = null;
								ReleaseNodes();
							}*/
						}
					}
				}
			}

			m_init = true;
		}
		
		private boolean HandleSerialApiSetTimeoutsResponse(byte[] data){
			// the meaning of this command and its response is currently unclear
			boolean res = true;
			Log.i(TAG, "Received reply to FUNC_ID_SERIAL_API_SET_TIMEOUTS");
			return res;
		}
	}
	
	private class SendThread extends Thread{
		private OutputStream out;
		//private InputStream in;
		private int timeout;
		boolean eventSet;
		public SendThread(ThreadGroup tg, PL2303driver _pl2303){
			super(tg, "sendThread");
			this.out = _pl2303.getOutputStream();
			//this.in = _pl2303.getInputStream();
		}
		public void run(){
			timeout = -1;
			while(connected)
			{
				try {
					Thread.sleep(5);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				if(reading)
					continue;
				
				eventSet = sendEvent.Wait(timeout);
				
				if(!connected)
					return;
				
				if(eventSet){
					synchronized(queueMutex){
						if(!sendQueue.isEmpty()){
							Msg msg = sendQueue.peek();
							m_expectedCallbackId = msg.GetCallbackId();
							m_expectedReply = msg.GetExpectedReply();
							//m_expectedCommandClassId = msg.GetExpectedCommandClassId();
							m_waitingForAck = true;
							sendEvent.Reset();
							timeout = 10000;
							
							msg.SetSendAttempts((byte) (msg.GetSendAttempts()+0x01));
							
							Log.d(TAG,"Sending message (Callback ID="+HexDump.toHexString(m_expectedCallbackId)+", Expected Reply="+HexDump.toHexString(m_expectedReply)+") - "+msg.toString());
							
							try {
								out.write(msg.getBuffer());
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
						else{
							Log.d(TAG, "kees");
							/*byte nodeId = GetNodeInfoRequest();
							if( nodeId != 0xff )
							{
								Msg msg = new Msg( "Get Node Protocol Info", nodeId, Defines.REQUEST, Defines.FUNC_ID_ZW_GET_NODE_PROTOCOL_INFO, false );
								msg->Append( nodeId );
								SendMsg(msg);
							}*/
						}
					}
				}
				else{
					timeout = -1;
					synchronized(queueMutex){
						if( m_waitingForAck || (m_expectedCallbackId != 0x00) || (m_expectedReply != 0x00) )
						{
							// Timed out waiting for a response
							if( !sendQueue.isEmpty() )
							{
								Msg msg = sendQueue.peek();
	
								if( msg.GetSendAttempts() > 4 )
								{
									// Give up
									Log.d(TAG, "ERROR: Dropping command, expected response not received after five attempts");
									RemoveMsg();
								}
								else
								{
									// Resend
									if( msg.GetSendAttempts() > 0 )
									{
										Log.d(TAG, "Timeout" );
	
										// In case this is a sleeping node, we first try to move
										// its pending messages to its wake-up queue.
	
										// We can't have the send mutex locked until deeper into the move
										// messages method to avoid deadlocks with the node mutex.
										/*if( !MoveMessagesToWakeUpQueue( msg->GetTargetNodeId() ) )
										{
											// The attempt failed, so the node is either not a sleeping node, or the move
											// failed for another reason.  We'll just retry sending the message.
											Log::Write( "Resending message (attempt %d)", msg->GetSendAttempts() );
										}*/
										//m_sendMutex->Lock();
									}
								}
	
								m_waitingForAck = false;
								m_expectedCallbackId = 0;
								m_expectedReply = 0;
								//m_expectedCommandClassId = 0;
								UpdateEvents();
							}
						}
					}
				}
			}
		}
	}
	
	private class Mutex{
		public Mutex(){
		}
	}
	
	private enum MsgQueueCmd{
		MsgQueueCmd_SendMsg,
		MsgQueueCmd_QueryStageComplete,
		MsgQueueCmd_Controller
	};
	
	public enum ControllerCommand
	{
		ControllerCommand_None,					/**< No command. */
		ControllerCommand_AddDevice,					/**< Add a new device or controller to the Z-Wave network. */
		ControllerCommand_CreateNewPrimary,				/**< Add a new controller to the Z-Wave network. Used when old primary fails. Requires SUC. */
		ControllerCommand_ReceiveConfiguration,				/**< Receive Z-Wave network configuration information from another controller. */
		ControllerCommand_RemoveDevice,					/**< Remove a device or controller from the Z-Wave network. */
		ControllerCommand_RemoveFailedNode,				/**< Move a node to the controller's failed nodes list. This command will only work if the node cannot respond. */
		ControllerCommand_HasNodeFailed,				/**< Check whether a node is in the controller's failed nodes list. */
		ControllerCommand_ReplaceFailedNode,				/**< Replace a non-responding node with another. The node must be in the controller's list of failed nodes for this command to succeed. */
		ControllerCommand_TransferPrimaryRole,				/**< Make a different controller the primary. */
		ControllerCommand_RequestNetworkUpdate,				/**< Request network information from the SUC/SIS. */
		ControllerCommand_RequestNodeNeighborUpdate,			/**< Get a node to rebuild its neighbour list.  This method also does RequestNodeNeighbors */
		ControllerCommand_AssignReturnRoute,				/**< Assign a network return routes to a device. */
		ControllerCommand_DeleteAllReturnRoutes,			/**< Delete all return routes from a device. */
		ControllerCommand_SendNodeInformation,				/**< Send a node information frame */
		ControllerCommand_ReplicationSend,				/**< Send information from primary to secondary */
		ControllerCommand_CreateButton,					/**< Create an id that tracks handheld button presses */
		ControllerCommand_DeleteButton					/**< Delete id that tracks handheld button presses */
	};
	
	public enum ControllerState
	{
		ControllerState_Normal,				/**< No command in progress. */
		ControllerState_Starting,				/**< The command is starting. */
		ControllerState_Cancel,					/**< The command was cancelled. */
		ControllerState_Error,					/**< Command invocation had error(s) and was aborted */
		ControllerState_Waiting,				/**< Controller is waiting for a user action. */
		ControllerState_Sleeping,				/**< Controller command is on a sleep queue wait for device. */
		ControllerState_InProgress,				/**< The controller is communicating with the other device to carry out the command. */
		ControllerState_Completed,			    	/**< The command has completed successfully. */
		ControllerState_Failed,					/**< The command has failed. */
		ControllerState_NodeOK,					/**< Used only with ControllerCommand_HasNodeFailed to indicate that the controller thinks the node is OK. */
		ControllerState_NodeFailed				/**< Used only with ControllerCommand_HasNodeFailed to indicate that the controller thinks the node has failed. */
	};
	
	private class ControllerCommandItem{
		ControllerState				m_controllerState;
		boolean						m_controllerStateChanged;
		boolean						m_controllerCommandDone;
		ControllerCommand			m_controllerCommand;
		//pfnControllerCallback_t	m_controllerCallback;
		//ControllerError			m_controllerReturnError;
		//void						m_controllerCallbackContext;
		boolean						m_highPower;
		boolean						m_controllerAdded;
		byte						m_controllerCommandNode;
		byte						m_controllerCommandArg;
	}
	
	private class MsgQueueItem
	{
		public boolean equals(Object o){
			if (this == o) return true;
			
			if ( !(o instanceof MsgQueueItem) ) return false;
			
			MsgQueueItem mqi = (MsgQueueItem)o;
			
			if(mqi.m_command == this.m_command){
				if(m_command == MsgQueueCmd.MsgQueueCmd_SendMsg){
					return((mqi.m_msg.equals(m_msg)));
				}
				else if(m_command == MsgQueueCmd.MsgQueueCmd_QueryStageComplete){
					return( (mqi.m_nodeId == m_nodeId) && (mqi.m_queryStage == m_queryStage) );
				}
				else if(m_command == MsgQueueCmd.MsgQueueCmd_Controller){
					return( (mqi.m_cci.m_controllerCommand == m_cci.m_controllerCommand) && (mqi.m_cci.m_controllerCallback == m_cci.m_controllerCallback) );
				}
			}
			
			return false;
		}

		MsgQueueCmd				m_command;
		Msg						m_msg;
		byte					m_nodeId;
		QueryStage				m_queryStage;
		boolean					m_retry;
		ControllerCommandItem	m_cci;
	};
}