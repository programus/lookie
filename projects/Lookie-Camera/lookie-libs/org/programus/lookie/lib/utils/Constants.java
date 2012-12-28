package org.programus.lookie.lib.utils;

public interface Constants {
	String BR = "\n";
	int REQUEST_ENABLE_BT = 5;
	int MSG_WHAT_ROBOT_CONNECT = 0x02;
	int MSG_WHAT_CAMERA_CONNECT = 0x01;
	int MSG_WHAT_EXCEPTION = 0x04;
	int MSG_WHAT_DATA_READ = 0x08;
	int MSG_WHAT_CAM_READ = 0x09;
	int MSG_WHAT_GET_ANGLE = 0x0a;
	int MSG_WHAT_TAKE_PICTURE = 0x0b;
	int MSG_WHAT_LOG = 0x10;
	
	String CAMERA_UUID = "d03c8970-4f5c-11e2-bcfd-0800200c9a66";
	String CAMERA_BT_NAME = "Lookie - Eye";
	
	String KEY_ROBOT_CONNECT_STATUS = "Key.RobotConnectStatus";
	String KEY_CAMERA_CONNECT_STATUS = "Key.CameraConnectStatus";
	String KEY_CAMERA_DEVICE = "Key.CameraDevice";
	String KEY_EXCEPTION = "Key.IOException";
	String KEY_MESSAGE = "Key.Message";
	String KEY_NXT_DATA = "Key.NXTData";
	String KEY_CAM_CMD = "Key.CameraCommand";
	String KEY_ANGLE = "Key.Angle";
	String KEY_LOG = "Key.Log";
	
	int CONN_STATUS_CONNECTED = 2;
	int CONN_STATUS_DISCONNECTED = 0;
	int CONN_STATUS_CONNECTING = 1;
	
	int INVALID_POINTER_ID = -1;
	
	int COMPRESS_RATE = 30;
	int SIZE_MIN_WIDTH = 100;
	int SIZE_MAX_WIDTH = 800;
	// max byte per millisecond
	int MAX_BPMS = 25;
	
	int LEFT = 0;
	int RIGHT = 1;
	int MID = 2;
	int CALIBRATE = 4;
	int CAMERA = 8;
	int SIZE = 16;
	int NEAR = 32;
	int END = 0x00ff;
	
	int SEND_Q_SIZE = 1;
	int READ_Q_SIZE = 1;
}
