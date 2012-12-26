package org.programus.nxt.android.lookie_rc.comm;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import org.programus.lookie.lib.utils.Constants;
import org.programus.nxt.android.lookie_rc.parts.FriendBtDevice;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class CameraBtCommunicator {
	private final static String TAG = "CAM Comm";
	private Handler handler;
	private BluetoothAdapter btAdapter;
	private BluetoothSocket socket;
	
	public CameraBtCommunicator(BluetoothAdapter btAdapter, Handler handler) {
		this.btAdapter = btAdapter;
		this.handler = handler;
	}
	
	public void connect() {
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
				btAdapter.cancelDiscovery();
				for (BluetoothDevice device : pairedDevices) {
					if (socket == null) {
						connectDevice(device);
					}
				}
				
				if (socket == null) {
					notifyConnected(false);
				}
			}
		});
		t.setDaemon(true);
		t.start();
	}
	
	private void connectDevice(final BluetoothDevice device) {
		BluetoothSocket tmp = null;
		FriendBtDevice d = new FriendBtDevice(device);
		if (socket == null) {
			Log.d(TAG, String.format("Start connecting try: %s", d));
			try {
				synchronized(device) {
					tmp = device.createRfcommSocketToServiceRecord(UUID.fromString(Constants.CAMERA_UUID));
				}
			} catch (IOException e) {
				Log.w(TAG, "create socket failed.", e);
			}
		}
		Log.d(TAG, String.format("Tried: %s, Result: %s", d, String.valueOf(tmp)));
		try {
			tmp.connect();
			if (tmp != null && socket == null) {
				socket = tmp;
				notifyConnected(true);
			}
		} catch (IOException e) {
			Log.w(TAG, "Connect failed: " + d);
		}
	}
	
	private void notifyConnected(boolean connected) {
		Bundle b = new Bundle();
		b.putInt(Constants.KEY_CAMERA_CONNECT_STATUS, connected ? Constants.CONN_STATUS_CONNECTED : Constants.CONN_STATUS_DISCONNECTED);
		
		Message msg = new Message();
		msg.setData(b);
		msg.what = Constants.MSG_WHAT_CAMERA_CONNECT;
		handler.sendMessage(msg);
	}
	
	public void end() throws IOException {
		this.socket.close();
	}
}
