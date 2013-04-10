package nl.willemburgers.android.z_droid;

import java.util.HashMap;

import nl.willemburgers.android.z_droid.Defines.Defines;
import nl.willemburgers.android.z_droid.ZWaveController.MsgQueue;

public class Node {
	private final String TAG = "Z-Droid";
	
	private ZWaveController zwc;
	
	private QueryStage	m_queryStage;
	private boolean		m_queryPending = false;
	private boolean		m_queryConfiguration = false;
	private byte		m_queryRetries = 0;
	private boolean		m_protocolInfoReceived = false;
	private boolean		m_nodeInfoReceived = false;
	private boolean		m_manufacturerSpecificClassReceived = false;
	private boolean		m_nodeInfoSupported = true;
	private boolean		m_nodeAlive = true;
	private boolean		m_listening = true;
	private boolean		m_frequentListening = false;
	private boolean		m_beaming = false;
	private boolean		m_routing = false;
	private long		m_maxBaudRate = 0;
	private byte		m_version = 0;
	private boolean		m_security = false;
	private long		m_homeId;
	private byte		m_nodeId;
	private byte		m_basic = 0;		//*< Basic device class (0x01-Controller, 0x02-Static Controller, 0x03-Slave, 0x04-Routing Slave
	private byte		m_generic = 0;
	private byte		m_specific = 0;
	private String		m_type = "";			// Label representing the specific/generic/basic value
	//private byte		m_neighbors[29];	// Bitmask containing the neighbouring nodes
	private byte		m_numRouteNodes = 0;	// number of node routes
	//private byte		m_routeNodes[5];	// nodes to route to
	//map<uint8,uint8>	m_buttonMap;	// Map button IDs into virtual node numbers
	private String		m_manufacturerName = "";
	private String		m_productName = "";
	private String		m_nodeName = "";
	private String		m_location = "";

	private String		m_manufacturerId = "";
	private String		m_productType = "";
	private String		m_productId = "";
	
	//private HashMap<Byte,CommandClass>	m_commandClassMap = new HashMap<Byte,CommandClass>();	/**< Map of command class ids and pointers to associated command class objects */
	
	public enum QueryStage {
		QueryStage_ProtocolInfo,				/**< Retrieve protocol information */
		QueryStage_Probe,						/**< Ping device to see if alive */
		QueryStage_WakeUp,						/**< Start wake up process if a sleeping node */
		QueryStage_ManufacturerSpecific1,		/**< Retrieve manufacturer name and product ids if ProtocolInfo lets us */
		QueryStage_NodeInfo,					/**< Retrieve info about supported, controlled command classes */
		QueryStage_ManufacturerSpecific2,		/**< Retrieve manufacturer name and product ids */
		QueryStage_Versions,					/**< Retrieve version information */
		QueryStage_Instances,					/**< Retrieve information about multiple command class instances */
		QueryStage_Static,						/**< Retrieve static information (doesn't change) */
		QueryStage_Probe1,						/**< Ping a device upon starting with configuration */
		QueryStage_Associations,				/**< Retrieve information about associations */
		QueryStage_Neighbors,					/**< Retrieve node neighbor list */
		QueryStage_Session,						/**< Retrieve session information (changes infrequently) */
		QueryStage_Dynamic,						/**< Retrieve dynamic information (changes frequently) */
		QueryStage_Configuration,				/**< Retrieve configurable parameter information (only done on request) */
		QueryStage_Complete,					/**< Query process is completed for this node */
		QueryStage_None							/**< Query process hasn't started for this node */
	}
	
	public Node(ZWaveController _zwc, long _homeId, byte _nodeId) {
		this.zwc = _zwc;
		this.m_homeId = _homeId;
		this.m_nodeId = _nodeId;
		//AddCommandClass( 0 );
	}
	
	public void AdvanceQueries(){
		// For OpenZWave to discover everything about a node, we have to follow a certain
		// order of queries, because the results of one stage may affect what is requested
		// in the next stage.  The stage is saved with the node data, so that any incomplete
		// queries can be restarted the next time the application runs.
		// The individual command classes also store some state as to whether they have
		// had a response to certain queries.  This state is initilized by the SetStaticRequests
		// call in QueryStage_None.  It is also saved, so we do not need to request state
		// from every command class if some have previously responded.
		//
		// Each stage must generate all the messages for its particular	stage as
		// assumptions are made in later code (RemoveMsg) that this is the case. This means
		// each stage is only visited once.

		//Log.d(TAG, "AdvanceQueries queryPending=%d queryRetries=%d queryStage=%s live=%d", m_queryPending, m_queryRetries, c_queryStageNames[m_queryStage], m_nodeAlive );
		boolean addQSC = false;			// We only want to add a query stage complete if we did some work.
		while( !m_queryPending && m_nodeAlive )
		{
			switch( m_queryStage )
			{
				case QueryStage_None:
				{
					// Init the node query process
					m_queryStage = QueryStage.QueryStage_ProtocolInfo;
					m_queryRetries = 0;
					break;
				}
				case QueryStage_ProtocolInfo:
				{
					// determines, among other things, whether this node is a listener, its maximum baud rate and its device classes
					if( !ProtocolInfoReceived() )
					{
						//Log::Write( LogLevel_Detail, m_nodeId, "QueryStage_ProtocolInfo" );
						Msg msg = new Msg( "Get Node Protocol Info", m_nodeId, Defines.REQUEST, Defines.FUNC_ID_ZW_GET_NODE_PROTOCOL_INFO, false );
						msg.Append( m_nodeId );
						//GetDriver()->SendMsg( msg, Driver::MsgQueue_Query );
						zwc.SendMsg(msg, MsgQueue.MsgQueue_Query);
						m_queryPending = true;
						addQSC = true;
					}
					else
					{
						// This stage has been done already, so move to the Neighbours stage
						m_queryStage = QueryStage.QueryStage_Probe;
						m_queryRetries = 0;
					}
					break;
				}
				/*case QueryStage_Probe:
				{
					//Log::Write( LogLevel_Detail, m_nodeId, "QueryStage_Probe" );
					//
					// Send a NoOperation message to see if the node is awake
					// and alive. Based on the response or lack of response
					// will determine next step.
					//
					NoOperation* noop = static_cast<NoOperation*>( GetCommandClass( NoOperation::StaticGetCommandClassId() ) );
					if( GetDriver()->GetNodeId() != m_nodeId )
					{
						noop->Set( true );
					      	m_queryPending = true;
						addQSC = true;
					}
					else
					{
						m_queryStage = QueryStage.QueryStage_WakeUp;
						m_queryRetries = 0;
					}
					break;
				}
				case QueryStage_WakeUp:
				{
					// For sleeping devices other than controllers, we need to defer the usual requests until
					// we have told the device to send it's wake-up notifications to the PC controller.
					Log::Write( LogLevel_Detail, m_nodeId, "QueryStage_WakeUp" );

					WakeUp* wakeUp = static_cast<WakeUp*>( GetCommandClass( WakeUp::StaticGetCommandClassId() ) );

					// if this device is a "sleeping device" and not a controller and not a
					// FLiRS device. FLiRS will wake up when you send them something and they
					// don't seem to support Wakeup
					if( wakeUp && !IsController() && !IsFrequentListeningDevice() )
					{
						// start the process of requesting node state from this sleeping device
						wakeUp->Init();
						m_queryPending = true;
						addQSC = true;
					}
					else
					{
						// this is not a sleeping device, so move to the ManufacturerSpecific1 stage
						m_queryStage = QueryStage_ManufacturerSpecific1;
						m_queryRetries = 0;
					}
					break;
				}
				case QueryStage_ManufacturerSpecific1:
				{
					// Obtain manufacturer, product type and product ID code from the node device
					// Manufacturer Specific data is requested before the other command class data so
					// that we can modify the supported command classes list through the product XML files.
					//Log::Write( LogLevel_Detail, m_nodeId, "QueryStage_ManufacturerSpecific1" );
					if( GetDriver()->GetNodeId() == m_nodeId )
					{
						String configPath = ManufacturerSpecific::SetProductDetails( this, GetDriver()->GetManufacturerId(), GetDriver()->GetProductType(), GetDriver()->GetProductId() );
						if( configPath.length() > 0 )
						{
							ManufacturerSpecific::LoadConfigXML( this, configPath );
						}
						m_queryStage = QueryStage_NodeInfo;
						m_queryRetries = 0;
					}
					else
					{
						ManufacturerSpecific* cc = static_cast<ManufacturerSpecific*>( GetCommandClass( ManufacturerSpecific::StaticGetCommandClassId() ) );
						if( cc  )
						{
							m_queryPending = cc->RequestState( CommandClass::RequestFlag_Static, 1, Driver::MsgQueue_Query );
							addQSC = m_queryPending;
						}
						if( !m_queryPending )
						{
							m_queryStage = QueryStage_NodeInfo;
							m_queryRetries = 0;
						}
					}
					break;
				}
				case QueryStage_NodeInfo:
				{
					if( !NodeInfoReceived() && m_nodeInfoSupported )
					{
						// obtain from the node a list of command classes that it 1) supports and 2) controls (separated by a mark in the buffer)
						//Log::Write( LogLevel_Detail, m_nodeId, "QueryStage_NodeInfo" );
						Msg msg = new Msg( "Request Node Info", m_nodeId, REQUEST, FUNC_ID_ZW_REQUEST_NODE_INFO, false, true, FUNC_ID_ZW_APPLICATION_UPDATE );
						msg.Append( m_nodeId );
						//GetDriver()->SendMsg( msg, Driver::MsgQueue_Query );
						m_queryPending = true;
						addQSC = true;
					}
					else
					{
						// This stage has been done already, so move to the Manufacturer Specific stage
						m_queryStage = QueryStage_ManufacturerSpecific2;
						m_queryRetries = 0;
					}
					break;
				}
				case QueryStage_ManufacturerSpecific2:
				{
					if( !m_manufacturerSpecificClassReceived )
					{
						// Obtain manufacturer, product type and product ID code from the node device
						// Manufacturer Specific data is requested before the other command class data so
						// that we can modify the supported command classes list through the product XML files.
						/*Log::Write( LogLevel_Detail, m_nodeId, "QueryStage_ManufacturerSpecific2" );
						ManufacturerSpecific* cc = static_cast<ManufacturerSpecific*>( GetCommandClass( ManufacturerSpecific::StaticGetCommandClassId() ) );
						if( cc  )
						{
							m_queryPending = cc->RequestState( CommandClass::RequestFlag_Static, 1, Driver::MsgQueue_Query );
							addQSC = m_queryPending;
						}
						if( !m_queryPending )
						{
							m_queryStage = QueryStage_Versions;
							m_queryRetries = 0;
						}
					}
					else
					{
						/*ManufacturerSpecific* cc = static_cast<ManufacturerSpecific*>( GetCommandClass( ManufacturerSpecific::StaticGetCommandClassId() ) );
						if( cc  )
						{
							cc->ReLoadConfigXML();
						}
						m_queryStage = QueryStage_Versions;
						m_queryRetries = 0;
					}
					break;
				}
				case QueryStage_Versions:
				{
					// Get the version information (if the device supports COMMAND_CLASS_VERSION
					Log::Write( LogLevel_Detail, m_nodeId, "QueryStage_Versions" );
					Version* vcc = static_cast<Version*>( GetCommandClass( Version::StaticGetCommandClassId() ) );
					if( vcc )
					{
						for( map<uint8,CommandClass*>::const_iterator it = m_commandClassMap.begin(); it != m_commandClassMap.end(); ++it )
						{
							CommandClass* cc = it->second;
							if( cc->GetMaxVersion() > 1 )
							{
								// Get the version for each supported command class that
								// we have implemented at greater than version one.
								m_queryPending |= vcc->RequestCommandClassVersion( it->second );
							}
						}
						addQSC = m_queryPending;
					}
					// advance to Instances stage when finished
					if( !m_queryPending )
					{
						m_queryStage = QueryStage_Instances;
						m_queryRetries = 0;
					}
					break;
				}
				case QueryStage_Instances:
				{
					// if the device at this node supports multiple instances, obtain a list of these instances
					Log::Write( LogLevel_Detail, m_nodeId, "QueryStage_Instances" );
					MultiInstance* micc = static_cast<MultiInstance*>( GetCommandClass( MultiInstance::StaticGetCommandClassId() ) );
					if( micc )
					{
						m_queryPending = micc->RequestInstances();
						addQSC = m_queryPending;
					}

					// when done, advance to the Static stage
					if( !m_queryPending )
					{
						m_queryStage = QueryStage_Static;
						m_queryRetries = 0;

						Log::Write( LogLevel_Info, m_nodeId, "Essential node queries are complete" );
						Notification* notification = new Notification( Notification::Type_EssentialNodeQueriesComplete );
						notification->SetHomeAndNodeIds( m_homeId, m_nodeId );
						GetDriver()->QueueNotification( notification );
					}
					break;
				}
				case QueryStage_Static:
				{
					// Request any other static values associated with each command class supported by this node
					// examples are supported thermostat operating modes, setpoints and fan modes
					Log::Write( LogLevel_Detail, m_nodeId, "QueryStage_Static" );
					for( map<uint8,CommandClass*>::const_iterator it = m_commandClassMap.begin(); it != m_commandClassMap.end(); ++it )
					{
						if( !it->second->IsAfterMark() )
						{
							m_queryPending |= it->second->RequestStateForAllInstances( CommandClass::RequestFlag_Static, Driver::MsgQueue_Query );
						}
					}
					addQSC = m_queryPending;

					if( !m_queryPending )
					{
						// when all (if any) static information has been retrieved, advance to the Associations stage
						m_queryStage = QueryStage_Associations;
						m_queryRetries = 0;
					}
					break;
				}
				case QueryStage_Probe1:
				{
					Log::Write( LogLevel_Detail, m_nodeId, "QueryStage_Probe1" );
					//
					// Send a NoOperation message to see if the node is awake
					// and alive. Based on the response or lack of response
					// will determine next step. Called here when configuration exists.
					//
					//NoOperation* noop = static_cast<NoOperation*>( GetCommandClass( NoOperation::StaticGetCommandClassId() ) );
					if( zwc.GetNodeId() != m_nodeId )
					{
						//noop->Set( true );
						m_queryPending = true;
						addQSC = true;
					}
					else
					{
						m_queryStage = QueryStage_Associations;
						m_queryRetries = 0;
					}
					break;
				}
				case QueryStage_Associations:
				{
					// if this device supports COMMAND_CLASS_ASSOCIATION, determine to which groups this node belong
					Log::Write( LogLevel_Detail, m_nodeId, "QueryStage_Associations" );
					Association* acc = static_cast<Association*>( GetCommandClass( Association::StaticGetCommandClassId() ) );
					if( acc )
					{
						acc->RequestAllGroups( 0 );
						m_queryPending = true;
						addQSC = true;
					}
					else
					{
						// if this device doesn't support Associations, move to retrieve Session information
						m_queryStage = QueryStage_Neighbors;
						m_queryRetries = 0;
					}
					break;
				}
				case QueryStage_Neighbors:
				{
					// retrieves this node's neighbors and stores the neighbor bitmap in the node object
					Log::Write( LogLevel_Detail, m_nodeId, "QueryStage_Neighbors" );
					GetDriver()->RequestNodeNeighbors( m_nodeId, 0 );
					m_queryPending = true;
					addQSC = true;
					break;
				}
				case QueryStage_Session:
				{
					// Request the session values from the command classes in turn
					// examples of Session information are: current thermostat setpoints, node names and climate control schedules
					Log::Write( LogLevel_Detail, m_nodeId, "QueryStage_Session" );
					for( map<uint8,CommandClass*>::const_iterator it = m_commandClassMap.begin(); it != m_commandClassMap.end(); ++it )
					{
						if( !it->second->IsAfterMark() )
						{
							m_queryPending |= it->second->RequestStateForAllInstances( CommandClass::RequestFlag_Session, Driver::MsgQueue_Query );
						}
					}
					addQSC = m_queryPending;
					if( !m_queryPending )
					{
						m_queryStage = QueryStage_Dynamic;
						m_queryRetries = 0;
					}
					break;
				}
				case QueryStage_Dynamic:
				{
					// Request the dynamic values from the node, that can change at any time
					// Examples include on/off state, heating mode, temperature, etc.
					Log::Write( LogLevel_Detail, m_nodeId, "QueryStage_Dynamic" );
					m_queryPending = RequestDynamicValues();
					addQSC = m_queryPending;

					if( !m_queryPending )
					{
						m_queryStage = QueryStage_Configuration;
						m_queryRetries = 0;
					}
					break;
				}
				case QueryStage_Configuration:
				{
					// Request the configurable parameter values from the node.
					Log::Write( LogLevel_Detail, m_nodeId, "QueryStage_Configuration" );
					if( m_queryConfiguration )
					{
						if( RequestAllConfigParams( 0 ) )
						{
							m_queryPending = true;
							addQSC = true;
						}
						m_queryConfiguration = false;
					}
					if( !m_queryPending )
					{
						m_queryStage = QueryStage_Complete;
						m_queryRetries = 0;
					}
					break;
				}
				case QueryStage_Complete:
				{
					// Notify the watchers that the queries are complete for this node
					Log::Write( LogLevel_Detail, m_nodeId, "QueryStage_Complete" );
					Notification* notification = new Notification( Notification::Type_NodeQueriesComplete );
					notification->SetHomeAndNodeIds( m_homeId, m_nodeId );
					GetDriver()->QueueNotification( notification );

					// Check whether all nodes are now complete
					GetDriver()->CheckCompletedNodeQueries();
					return;
				}*/
				default:
				{
					break;
				}
			}
		}

		if( addQSC && m_nodeAlive )
		{
			// Add a marker to the query queue so this advance method
			// gets called again once this stage has completed.
			//zwc.SendQueryStageComplete( m_nodeId, m_queryStage );
		}
	}
	
	public void SetQueryStage (QueryStage _stage, boolean _advance)
	{
		if( _stage.ordinal() < m_queryStage.ordinal() )
		{
			m_queryStage = _stage;
			m_queryPending = false;

			if( QueryStage.QueryStage_Configuration == _stage )
			{
				m_queryConfiguration = true;
			}
		}

		if( _advance )
		{
			AdvanceQueries();
		}
	}
	
	public boolean ProtocolInfoReceived(){ return m_protocolInfoReceived; }
	
	/*private CommandClass AddCommandClass(byte _commandClassId){
			if( GetCommandClass( _commandClassId ) )
			{
				// Class and instance have already been added
				return null;
			}

			// Create the command class object and add it to our map
			if( CommandClass pCommandClass = CommandClasses::CreateCommandClass( _commandClassId, m_homeId, m_nodeId ) )
			{
				m_commandClassMap[_commandClassId] = pCommandClass;
				return pCommandClass;
			}
			else
			{
				//Log::Write( LogLevel_Info, m_nodeId, "AddCommandClass - Unsupported Command Class 0x%.2x", _commandClassId );
			}

			return null;
		}*/
}
