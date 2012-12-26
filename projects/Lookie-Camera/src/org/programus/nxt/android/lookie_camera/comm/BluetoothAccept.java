package org.programus.nxt.android.lookie_camera.comm;

import java.io.IOException;
import java.util.UUID;

import org.programus.lookie.lib.utils.Constants;
import org.programus.nxt.android.lookie_camera.services.MainService;
import org.programus.nxt.android.lookie_camera.utils.Logger;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BluetoothAccept implements Runnable {
	private final static String TAG = "BTAccept";
	private Context context;
	private BluetoothServerSocket server;
	private BluetoothSocket socket;
	private Logger logger = Logger.getInstance();
	
	public BluetoothAccept(BluetoothAdapter btAdapter, Context context) {
		try {
			logger.log("Listen on UUID: " + Constants.CAMERA_UUID);
			this.server = btAdapter.listenUsingRfcommWithServiceRecord(Constants.CAMERA_BT_NAME, UUID.fromString(Constants.CAMERA_UUID));
		} catch (IOException e) {
			logger.log("listen error.");
			Log.d(TAG, "Listen error.", e);
		}
		this.context = context;
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
		Intent intent = new Intent(context, MainService.class);
		intent.putExtra(MainService.START_FLAG_KEY, MainService.START_FLAG_BT_CONNECTED);
		context.startService(intent);
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
