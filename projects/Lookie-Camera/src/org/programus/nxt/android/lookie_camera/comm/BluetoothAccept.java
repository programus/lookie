package org.programus.nxt.android.lookie_camera.comm;

import java.io.IOException;
import java.util.UUID;

import org.programus.lookie.lib.utils.Constants;
import org.programus.nxt.android.lookie_camera.utils.Logger;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

public class BluetoothAccept implements Runnable {
	public static interface OnConnectedCallback {
		void onConnected(BluetoothSocket socket, BluetoothAccept accept);
	}
	
	private final static String TAG = "BTAccept";
	private BluetoothServerSocket server;
	private BluetoothSocket socket;
	private Logger logger = Logger.getInstance();
	
	private OnConnectedCallback onConnectedCallback;
	
	public BluetoothAccept(BluetoothAdapter btAdapter) {
		try {
			logger.log("Listen on UUID: " + Constants.CAMERA_UUID);
			this.server = btAdapter.listenUsingRfcommWithServiceRecord(Constants.CAMERA_BT_NAME, UUID.fromString(Constants.CAMERA_UUID));
		} catch (IOException e) {
			logger.log("listen error.");
			Log.d(TAG, "Listen error.", e);
		}
	}

	@Override
	public void run() {
		while (true) {
			try {
				logger.log("Start listenning...");
				this.socket = server.accept();
			} catch (IOException e) {
				break;
			}
			
			if (this.socket != null) {
				this.notifyConnected();
				try {
					this.server.close();
				} catch (IOException e) {
					logger.log("Close server error");
					Log.d(TAG, "Close server error.", e);
				}
				break;
			}
		}
	}
	
	private void notifyConnected() {
		if (this.onConnectedCallback != null) {
			this.onConnectedCallback.onConnected(socket, this);
		}
	}
	
	public void setOnConnectedCallback(OnConnectedCallback callback) {
		this.onConnectedCallback = callback;
	}
	
	public BluetoothSocket getSocket() {
		return this.socket;
	}
	
	public void cancel() {
		try {
			this.server.close();
		} catch (IOException e) {
			Log.d(TAG, "Close server error.", e);
		}
	}

}
