package org.programus.nxt.android.lookie_rc.comm;

import java.io.IOException;
import java.io.ObjectOutputStream;

import org.programus.lookie.lib.comm.CameraCommand;
import org.programus.lookie.lib.utils.Constants;
import org.programus.lookie.lib.utils.SimpleQueue;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class CameraCommandSender implements Runnable {
	private final static String TAG = "Cam Reader";
	private boolean running = true;
	
	private ObjectOutputStream out;
	private Handler handler;
	private SimpleQueue<CameraCommand> q = DataBuffer.getInstance().getCamSendQueue();
	
	public CameraCommandSender(ObjectOutputStream out, Handler handler) {
		this.out = out;
		this.handler = handler;
	}

	@Override
	public void run() {
		while (this.running) {
			CameraCommand cmd = null;
			while (q.isEmpty() && this.running) {
				Thread.yield();
			}
			try {
				synchronized(q) {
					cmd = q.poll();
				}
				synchronized(out) {
					out.writeObject(cmd);
					Log.d(TAG, String.format("Send => %s", cmd.toString()));
				}
			} catch (IOException e) {
				this.notifyException(e);
				Thread.yield();
			}
			Thread.yield();
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
				this.notifyException(e);
			} finally {
				try {
					out.close();
				} catch (IOException e) {
					this.notifyException(e);
				}
			}
		}
	}
	
	private void notifyException(Exception e) {
		Bundle b = new Bundle();
		b.putSerializable(Constants.KEY_EXCEPTION, e);
		b.putString(Constants.KEY_MESSAGE, "Error when send camera command.");
		
		Message msg = new Message();
		msg.setData(b);
		msg.what = Constants.MSG_WHAT_EXCEPTION;
		handler.sendMessage(msg);
	}
	
	public void end() {
		this.running = false;
	}
}
