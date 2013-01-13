package org.programus.nxt.android.lookie_camera.comm;

import org.programus.lookie.lib.comm.CameraCommand;
import org.programus.lookie.lib.utils.Constants;
import org.programus.lookie.lib.utils.SimpleQueue;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class CommandReader implements Runnable {
	private final static String TAG = "Reader";
	private boolean running = true;
	
	private Handler handler;
	private SimpleQueue<CameraCommand> readQ = DataBuffer.getInstance().getReadQueue();
	
	public CommandReader(Handler handler) {
		this.handler = handler;
	}

	@Override
	public void run() {
		this.running = true;
		while (this.running) {
			CameraCommand cmd = null;
			while (this.readQ.isEmpty() && this.running) {
				Thread.yield();
			}
			synchronized(this.readQ) {
				cmd = this.readQ.poll();
			}
			
			if (cmd != null) {
				Log.d(TAG, String.format("Read => %s", cmd.toString()));
				this.notifyCameraCommand(cmd);
			}
			Thread.yield();
		}
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
	}
}
