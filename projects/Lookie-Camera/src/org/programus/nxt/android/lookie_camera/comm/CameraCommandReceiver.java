package org.programus.nxt.android.lookie_camera.comm;

import java.io.IOException;
import java.io.ObjectInputStream;

import org.programus.lookie.lib.comm.CameraCommand;
import org.programus.lookie.lib.utils.Constants;
import org.programus.lookie.lib.utils.SimpleQueue;
import org.programus.nxt.android.lookie_camera.utils.Logger;

import android.util.Log;

public class CameraCommandReceiver implements Runnable {
	private final static String TAG = "Cam Reader";
	private boolean running = true;
	
	private SimpleQueue<CameraCommand> readQ = DataBuffer.getInstance().getReadQueue();
	
	private Logger logger = Logger.getInstance();
	
	private ObjectInputStream in;
	
	public CameraCommandReceiver(ObjectInputStream in) {
		this.in = in;
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
				this.processException(e);
				cmd = new CameraCommand();
				cmd.setCommand(Constants.END);
			} catch (ClassNotFoundException e) {
				this.processException(e);
				Thread.yield();
			}
			
			if (cmd != null) {
				this.readQ.offer(cmd);
			}
			Thread.yield();
		}
	}

	private void processException(Exception e) {
		SimpleQueue<CameraCommand> sendQ = DataBuffer.getInstance().getSendQueue();
		CameraCommand cmd = new CameraCommand();
		cmd.setCommand(Constants.END);
		sendQ.offer(cmd);
		this.running = false;
		logger.log("Error when read camera command.");
	}
	
	public void end() {
		this.running = false;
		if (this.in != null) {
			try {
				this.in.close();
			} catch (IOException e) {
				this.processException(e);
			}
		}
	}
}
