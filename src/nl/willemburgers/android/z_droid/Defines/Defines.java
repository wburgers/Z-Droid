package nl.willemburgers.android.z_droid.Defines;

public final class Defines {
	public static final int		MAX_TRIES									= 3;		// Retry sends up to 3 times
	public static final int		MAX_MAX_TRIES								= 7;		// Don't exceed this retry limit
	public static final int		ACK_TIMEOUT									= 1000;		// How long to wait for an ACK
	public static final int		BYTE_TIMEOUT								= 150;
	public static final int		RETRY_TIMEOUT								= 40000;	// Retry send after 40 seconds

	public static final byte	SOF											= (byte)	0x01;
	public static final byte	ACK											= (byte)	0x06;
	public static final byte	NAK											= (byte)	0x15;
	public static final byte	CAN											= (byte)	0x18;
	
	public static final int		NUM_NODE_BITFIELD_BYTES						= 29;
	
	public static final byte	REQUEST										= (byte)	0x00;
	public static final byte	RESPONSE									= (byte)	0x01;
	
	public static final byte	FUNC_ID_SERIAL_API_GET_INIT_DATA			= (byte)	0x02;
	public static final byte	FUNC_ID_SERIAL_API_APPL_NODE_INFORMATION	= (byte)	0x03;
	public static final byte	FUNC_ID_APPLICATION_COMMAND_HANDLER			= (byte)	0x04;
	public static final byte	FUNC_ID_ZW_GET_CONTROLLER_CAPABILITIES		= (byte)	0x05;
	public static final byte	FUNC_ID_SERIAL_API_SET_TIMEOUTS				= (byte)	0x06;
	public static final byte	FUNC_ID_SERIAL_API_GET_CAPABILITIES			= (byte)	0x07;
	
	public static final byte	FUNC_ID_ZW_SEND_DATA						= (byte)	0x13;
	public static final byte	FUNC_ID_ZW_GET_VERSION						= (byte)	0x15;
	public static final byte	FUNC_ID_ZW_GET_RANDOM						= (byte)	0x1c;
	public static final byte	FUNC_ID_ZW_MEMORY_GET_ID					= (byte)	0x20;
	
	public static final byte	FUNC_ID_ZW_GET_NODE_PROTOCOL_INFO			= (byte)	0x41;
	public static final byte	FUNC_ID_ZW_ENABLE_SUC						= (byte)	0x52;
	
	public static final byte	FUNC_ID_ZW_GET_VIRTUAL_NODES				= (byte)	0xA5;    // Return all virtual nodes
	
	public static final byte	APPLICATION_NODEINFO_LISTENING				= (byte)	0x01;
}