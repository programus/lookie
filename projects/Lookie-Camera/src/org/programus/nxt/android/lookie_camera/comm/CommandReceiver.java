package org.programus.nxt.android.lookie_camera.comm;

import org.programus.lookie.lib.comm.CameraCommand;
import org.programus.lookie.lib.utils.Constants;
import org.programus.lookie.lib.utils.SimpleQueue;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class CommandReceiver implements Runnable {
	private final static String TAG = "Reader";
	private boolean running = true;
	
	private Handler handler;
	private SimpleQueue<CameraCommand> readQ = DataBuffer.getInstance().getReadQueue();
	
	public CommandReceiver(Handler handler) {
		this.handler = handler;
	}

	@Override
	public void run() {
		while (this.running) {
			CameraCommand cmd = null;
			while (this.readQ.isEmpty()) {
				Thread.yield();
			}
			synchronized(this.readQ) {
				cmd = this.readQ.poll();
				Log.d(TAG, String.format("Read => %s", cmd.toString()));
			}
			
			if (cmd != null) {
				this.notifyCameraCommand(cmd);
			}
			Thread.yield();
		}
	}
	
	private void notifyCameraCommand(CameraCommand data) {
		Bundle b = new Bundle();
		b.putSerializable(Constants.KEY_CAM_CMD, data);
		
		Message msg = new Message();
		msg.setData(b);
		msg.what = Constants.MSG_WHAT_CAM_READ;
		handler.sendMessage(msg);
	}
	
	public void end() {
		this.running = false;
	}
}
