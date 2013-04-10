package nl.willemburgers.android.z_droid.Defines;

public final class ControllerDefines {
	// Controller Capabilities (return in FUNC_ID_ZW_GET_CONTROLLER_CAPABILITIES)
	public final static byte	ControllerCaps_Secondary		= 0x01;		// The controller is a secondary.
	public final static byte	ControllerCaps_OnOtherNetwork	= 0x02;		// The controller is not using its default HomeID.
	public final static byte	ControllerCaps_SIS				= 0x04;		// There is a SUC ID Server on the network.
	public final static byte	ControllerCaps_RealPrimary		= 0x08;		// Controller was the primary before the SIS was added.
	public final static byte	ControllerCaps_SUC				= 0x10;		// Controller is a static update controller.
		
	// Init Capabilities (return in FUNC_ID_SERIAL_API_GET_INIT_DATA)
	public final static byte	InitCaps_Slave					= 0x01;		//
	public final static byte	InitCaps_TimerSupport			= 0x02;		// Controller supports timers.
	public final static byte	InitCaps_Secondary				= 0x04;		// Controller is a secondary.
	public final static byte	InitCaps_SUC					= 0x08;		// Controller is a static update controller.
}
