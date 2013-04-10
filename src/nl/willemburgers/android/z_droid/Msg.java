package nl.willemburgers.android.z_droid;

import nl.willemburgers.android.z_droid.Defines.Defines;

import com.hoho.android.usbserial.util.*;

public class Msg {
	private String m_logText;
	private boolean m_bFinal = false;
	private boolean m_bCallbackRequired;
	
	private byte m_callbackId = 0;
	private byte m_expectedReply = 0;
	private byte m_expectedCommandClassId;
	
	private byte[] m_buffer = new byte[256];
	
	private byte m_length = 4;
	private byte m_targetNodeId;
	private byte m_sendAttempts = 0x00;
	private byte m_maxSendAttempts;
	
	private static byte s_nextCallbackId = 1;
	
	public Msg(final String _logText,
			byte _targetNodeId,
			final byte _msgType,
			final byte _function,
			final boolean _bCallbackRequired
			)
	{
		this(_logText, _targetNodeId, _msgType, _function, _bCallbackRequired, true, (byte)0x00, (byte)0x00);
	}
	
	public Msg(final String _logText,
			byte _targetNodeId,
			final byte _msgType,
			final byte _function,
			final boolean _bCallbackRequired,
			final boolean _bReplyRequired
			)
	{
		this(_logText, _targetNodeId, _msgType, _function, _bCallbackRequired, _bReplyRequired, (byte)0x00, (byte)0x00);
	}
	
	public Msg(final String _logText,
			byte _targetNodeId,
			final byte _msgType,
			final byte _function,
			final boolean _bCallbackRequired,
			final boolean _bReplyRequired,			// = true
			final byte _expectedReply,			// = 0
			final byte _expectedCommandClassId)
	{
		this.m_logText = _logText;
		this.m_bCallbackRequired = _bCallbackRequired;
		this.m_expectedCommandClassId = _expectedCommandClassId;
		this.m_targetNodeId = _targetNodeId;
		
		if( _bReplyRequired )
		{
			// Wait for this message before considering the transaction complete
			m_expectedReply = (_expectedReply != 0x00) ? _expectedReply : _function;
		}
		
		m_buffer[0] = Defines.SOF;
		m_buffer[1] = 0;					// Length of the following data, filled in during Finalize.
		m_buffer[2] = _msgType;
		m_buffer[3] = _function;
	}
	
	public void Append( final byte _data ){
		m_buffer[m_length++] = _data;
	}
	public void Finalize(){
		if( m_bFinal )
		{
			// Already finalized
			return;
		}
		if( m_bCallbackRequired )
		{
			// Set the length byte
			m_buffer[1] = (byte) m_length;		// Length of following data

			if( 0 == s_nextCallbackId )
			{
				s_nextCallbackId = 1;
			}

			m_buffer[m_length++] = s_nextCallbackId;
			m_callbackId = s_nextCallbackId++;
		}
		else
		{
			// Set the length byte
			m_buffer[1] = (byte) (m_length - 1);		// Length of following data
		}

		// Calculate the checksum
		byte checksum = (byte) 0xff;
		for( byte i=1; i<m_length; ++i )
		{
			checksum ^= m_buffer[i];
		}
		m_buffer[m_length++] = checksum;

		m_bFinal = true;
	}
	
	public byte[] getBuffer(){
		byte[] copy = new byte[this.m_length];
		System.arraycopy(m_buffer, 0, copy, 0, this.m_length);
		return copy;
	}
	
	public byte GetCallbackId(){
		return this.m_callbackId;
	}
	
	public byte GetExpectedReply(){
		return this.m_expectedReply;
	}
	
	public byte GetSendAttempts(){
		return this.m_sendAttempts;
	}
	
	public void SetSendAttempts(byte sendAttempts){
		this.m_sendAttempts = sendAttempts;
	}
	
	public String toString(){
		String str = m_logText + " ";
		if( m_targetNodeId != (byte) 0xff ){
			str += HexDump.toHexString(m_targetNodeId);
		}
		str += ": ";
		for( int i=0; i<m_length; ++i ){
			if( i!=0 ){
				str += ", ";
			}
			str += HexDump.toHexString(m_buffer[i]);
		}
		return str;
	}
	
	public boolean Equals(Object o){
		// TODO Auto-generated method stub
		
	}
}
