package org.programus.nxt.android.lookie_camera.comm;

import java.io.IOException;
import java.io.ObjectOutputStream;

import org.programus.lookie.lib.comm.CameraCommand;
import org.programus.lookie.lib.utils.Constants;
import org.programus.lookie.lib.utils.SimpleQueue;
import org.programus.nxt.android.lookie_camera.utils.Logger;

import android.util.Log;

public class CameraCommandSender implements Runnable {
	private final static String TAG = "Sender";
	private boolean running = true;
	
	private ObjectOutputStream out;
	private SimpleQueue<CameraCommand> sendQ = DataBuffer.getInstance().getSendQueue();
	
	private Logger logger = Logger.getInstance();

	public CameraCommandSender(ObjectOutputStream out) {
		this.out = out;
	}
	
	@Override
	public void run() {
		while (this.running) {
			while (sendQ.isEmpty() && this.running) {
				Thread.yield();
			}
			CameraCommand cmd = sendQ.poll();
			if (cmd != null) {
				try {
					synchronized(out) {
						Log.d(TAG, String.format("Send => %s", cmd.toString()));
						out.writeObject(cmd);
						out.flush();
					}
				} catch (IOException e) {
					this.processException(e);
					break;
				}
			}
		}
		this.sendEndSignal();
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
