package org.programus.nxt.android.lookie_rc.comm;

import java.io.DataInputStream;
import java.io.IOException;

import org.programus.lookie.lib.comm.CommandMessage;
import org.programus.lookie.lib.utils.Constants;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class CommandReceiver implements Runnable {
	private final static String TAG = "Reader";
	private boolean running = true;
	
	private DataInputStream in;
	private Handler handler;
	
	public CommandReceiver(DataInputStream in, Handler handler) {
		this.in = in;
		this.handler = handler;
	}

	@Override
	public void run() {
		while (this.running) {
			CommandMessage cmd = null;
			try {
				synchronized(in) {
					cmd = CommandMessage.read(in);
					Log.d(TAG, String.format("Read => %s", cmd.toString()));
				}
			} catch (IOException e) {
				this.notifyException(e);
				Thread.yield();
			}
			
			if (cmd != null) {
				NXTData data = new NXTData();
				data.setDir(cmd.getCommand());
				data.setSpeed(cmd.getData());
				this.notifyNXTData(data);
			}
			Thread.yield();
		}
	}
	
	private void notifyException(IOException e) {
		Bundle b = new Bundle();
		b.putSerializable(Constants.KEY_IOEXCEPTION, e);
		b.putString(Constants.KEY_MESSAGE, "Error when read command.");
		
		Message msg = new Message();
		msg.setData(b);
		msg.what = Constants.MSG_WHAT_IOEXCEPTION;
		handler.sendMessage(msg);
	}
	
	private void notifyNXTData(NXTData data) {
		Bundle b = new Bundle();
		b.putSerializable(Constants.KEY_NXT_DATA, data);
		
		Message msg = new Message();
		msg.setData(b);
		msg.what = Constants.MSG_WHAT_DATA_READ;
		handler.sendMessage(msg);
	}
	
	public void end() {
		this.running = false;
	}
}