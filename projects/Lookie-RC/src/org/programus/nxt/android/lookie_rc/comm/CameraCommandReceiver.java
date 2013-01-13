package org.programus.nxt.android.lookie_rc.comm;

import java.io.IOException;
import java.io.ObjectInputStream;

import org.programus.lookie.lib.comm.CameraCommand;
import org.programus.lookie.lib.utils.Constants;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class CameraCommandReceiver implements Runnable {
	private final static String TAG = "Cam Reader";
	private boolean running = true;
	
	private ObjectInputStream in;
	private Handler handler;
	
	public CameraCommandReceiver(ObjectInputStream in, Handler handler) {
		this.in = in;
		this.handler = handler;
	}

	@Override
	public void run() {
		while (this.running) {
			CameraCommand cmd = null;
			try {
				synchronized(in) {
					cmd = (CameraCommand) in.readObject();
					Log.d(TAG, String.format("Read => %s", cmd.toString()));
				}
			} catch (IOException e) {
				this.notifyException(e);
				Thread.yield();
			} catch (ClassNotFoundException e) {
				this.notifyException(e);
				Thread.yield();
			}
			
			if (cmd != null) {
				notifyCameraCommand(cmd);
			}
			Thread.yield();
		}
	}

	private void notifyException(Exception e) {
		Bundle b = new Bundle();
		b.putSerializable(Constants.KEY_EXCEPTION, e);
		b.putString(Constants.KEY_MESSAGE, "Error when read camera command.");
		
		Message msg = Message.obtain(handler, Constants.MSG_WHAT_EXCEPTION);
		msg.setData(b);
		msg.sendToTarget();
	}
	
	private void notifyCameraCommand(CameraCommand data) {
		Bundle b = new Bundle();
		b.putSerializable(Constants.KEY_CAM_CMD, data);
		
		Message msg = Message.obtain(handler, Constants.MSG_WHAT_CAM_READ);
		msg.setData(b);
		msg.sendToTarget();
	}
	
	public void end() {
		this.running = false;
		if (this.in != null) {
			try {
				this.in.close();
			} catch (IOException e) {
				this.notifyException(e);
			}
		}
	}
}
