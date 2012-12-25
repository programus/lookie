package org.programus.lookie.lib.utils;

public interface Constants {
	String BR = "\n";
	int REQUEST_ENABLE_BT = 5;
	int MSG_WHAT_ROBOT_CONNECT = 0x02;
	int MSG_WHAT_IOEXCEPTION = 0x04;
	int MSG_WHAT_DATA_READ = 0x08;
	
	String KEY_ROBOT_CONNECT_STATUS = "Key.RobotConnectStatus";
	String KEY_IOEXCEPTION = "Key.IOException";
	String KEY_MESSAGE = "Key.Message";
	String KEY_NXT_DATA = "Key.NXTData";
	
	int CONN_STATUS_CONNECTED = 2;
	int CONN_STATUS_DISCONNECTED = 0;
	int CONN_STATUS_CONNECTING = 1;
	
	int INVALID_POINTER_ID = -1;
	
	int LEFT = 0;
	int RIGHT = 1;
	int END = 0x00ff;
	
	int SEND_Q_SIZE = 1;
	int READ_Q_SIZE = 1;
}
