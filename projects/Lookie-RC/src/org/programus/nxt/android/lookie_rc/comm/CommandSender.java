package org.programus.nxt.android.lookie_rc.comm;

import java.io.DataOutputStream;
import java.io.IOException;

import org.programus.lookie.lib.comm.CommandMessage;
import org.programus.lookie.lib.utils.Constants;
import org.programus.lookie.lib.utils.SimpleQueue;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class CommandSender implements Runnable {
	private final static String TAG = "Sender";
	private boolean running = true;
	
	private DataOutputStream out;
	private Handler handler;
	private DataBuffer dbuff = DataBuffer.getInstance();

	public CommandSender(DataOutputStream out, Handler handler) {
		this.out = out;
		this.handler = handler;
	}
	
	@Override
	public void run() {
		while (this.running) {
			int count = 0;
			SimpleQueue<CommandMessage>[] qs = dbuff.getSendQueues();
			for (SimpleQueue<CommandMessage> q : qs) {
				CommandMessage cmd = q.poll();
				if (cmd != null) {
					try {
						synchronized(out) {
							Log.d(TAG, String.format("Send => %s", cmd.toString()));
							cmd.send(out);
							out.flush();
						}
						count++;
					} catch (IOException e) {
						this.notifyException(e);
						break;
					}
				}
			}
			if (count == 0) {
				Thread.yield();
			}
		}
		this.sendEndSignal();
	}
	
	private void sendEndSignal() {
		CommandMessage cmd = new CommandMessage();
		cmd.setCommand(Constants.END);
		cmd.setData(0);
		synchronized (out) {
			try {
				Log.d(TAG, String.format("Send => %s", cmd.toString()));
				cmd.send(out);
				out.flush();
			} catch (IOException e) {
				this.notifyException(e);
			}
		}
	}
	
	private void notifyException(IOException e) {
		Bundle b = new Bundle();
		b.putSerializable(Constants.KEY_IOEXCEPTION, e);
		b.putString(Constants.KEY_MESSAGE, "Error when send command.");
		
		Message msg = new Message();
		msg.setData(b);
		msg.what = Constants.MSG_WHAT_IOEXCEPTION;
		handler.sendMessage(msg);
	}
	
	public void end() {
		this.running = false;
	}
}
