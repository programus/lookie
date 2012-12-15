package org.programus.nxt.android.lookie_rc.comm;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import lejos.pc.comm.NXTCommFactory;
import lejos.pc.comm.NXTConnector;

import org.programus.nxt.android.lookie_rc.utils.Constants;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class RobotBtCommunicator implements Constants{
	private final static String TAG = "RobotBtComm";
	
	private NXTConnector connector;
	private Handler handler;
	private int connectStatus;
	
	private DataInputStream in;
	private DataOutputStream out;
	
	public RobotBtCommunicator(Handler handler) {
		this.handler = handler;
	}
	
	public void connect() {
		this.connect(null, null);
	}
	
	public void connect(final String name, final String address) {
		Log.d(TAG, String.format("Connect to %s (%s)", name, address));
		if (this.connectStatus == CONN_STATUS_DISCONNECTED) {
			this.connectStatus = CONN_STATUS_CONNECTING;
			new Thread(new Runnable() {
				@Override
				public void run() {
					connector = new NXTConnector();
					boolean connected = connector.connectTo(name, address, NXTCommFactory.BLUETOOTH);
					
					if (connected) {
						connectStatus = CONN_STATUS_CONNECTED;
						in = connector.getDataIn();
						out = connector.getDataOut();
					} else {
						connectStatus = CONN_STATUS_DISCONNECTED;
					}
					
					Bundle b = new Bundle();
					b.putInt(Constants.KEY_ROBOT_CONNECT_STATUS, connectStatus);
					
					Message msg = new Message();
					msg.setData(b);
					msg.what = Constants.MSG_WHAT_ROBOT_CONNECT;
					handler.sendMessage(msg);
				}
			}, "Robot Connect Thread").start();
		}
	}
	
	public void end() throws IOException {
		this.connector.close();
	}
}
