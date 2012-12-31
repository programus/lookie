package org.programus.nxt.android.lookie_camera.comm;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Queue;

import org.programus.lookie.lib.comm.CameraCommand;
import org.programus.lookie.lib.utils.Constants;
import org.programus.lookie.lib.utils.SimpleQueue;
import org.programus.nxt.android.lookie_camera.utils.Logger;

import android.util.Log;

public class CameraCommandSender implements Runnable {
	private final static String TAG = "Sender";
	private boolean running = true;
	
	private ObjectOutputStream out;
	private Queue<CameraCommand> sendQ = DataBuffer.getInstance().getSendQueue();
	private SimpleQueue<CameraCommand> imageQ = DataBuffer.getInstance().getImageQueue();
	
	private Logger logger = Logger.getInstance();

	public CameraCommandSender(ObjectOutputStream out) {
		this.out = out;
	}
	
	@Override
	public void run() {
		while (this.running) {
			while (sendQ.isEmpty() && imageQ.isEmpty() && this.running) {
				Thread.yield();
			}
			while (!sendQ.isEmpty()) {
				CameraCommand cmd = null;
				synchronized (sendQ) {
					cmd = sendQ.poll();
				}
				if (cmd != null) {
					if (!sendCommand(cmd)) {
						break;
					}
				}
				Thread.yield();
			}
			if (!imageQ.isEmpty()) {
				CameraCommand cmd = null;
				synchronized (imageQ) {
					cmd = imageQ.poll();
				}
				if (cmd != null) {
					if (!sendCommand(cmd)) {
						break;
					}
				}
			}
		}
		this.sendEndSignal();
	}
	
	private boolean sendCommand(CameraCommand cmd) {
		boolean ret = false;
		if (cmd != null) {
			try {
				synchronized(out) {
					Log.d(TAG, String.format("Send => %s", cmd.toString()));
					out.writeObject(cmd);
					out.flush();
					ret = true;
				}
			} catch (IOException e) {
				this.processException(e);
			}
		}
		
		return ret;
	}
	
	private void sendEndSignal() {
		CameraCommand cmd = new CameraCommand();
		cmd.setCommand(Constants.END);
		synchronized (out) {
			try {
				Log.d(TAG, String.format("Send => %s", cmd.toString()));
				out.writeObject(cmd);
				out.flush();
			} catch (IOException e) {
				this.processException(e);
			} finally {
				try {
					out.close();
				} catch (IOException e) {
					this.processException(e);
				}
			}
		}
	}
	
	private void processException(Exception e) {
		this.running = false;
		logger.log("Error when send camera command.");
	}
	
	public void end() {
		this.running = false;
	}
}
