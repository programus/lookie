package org.programus.nxj.lookie.nxt.comm;

import java.io.DataInputStream;
import java.io.IOException;

import lejos.nxt.Sound;

import org.programus.lookie.lib.comm.CommandMessage;
import org.programus.lookie.lib.utils.Constants;
import org.programus.lookie.lib.utils.SimpleQueue;
import org.programus.nxj.lookie.nxt.utils.Notifiable;
import org.programus.nxj.lookie.nxt.utils.NotifyTypes;

public class CommandReceiver implements Runnable {
	private boolean running = true;
	private DataInputStream in;
	private DataBuffer dbuff = DataBuffer.getInstance();
	
	private Notifiable notifier;
	
	public CommandReceiver(DataInputStream in, Notifiable notifier) {
		this.in = in;
		this.notifier = notifier;
	}

	@Override
	public void run() {
		while (this.running) {
			CommandMessage cmd = null;
			try {
				synchronized (in) {
					cmd = CommandMessage.read(in);
				}
			} catch (IOException e) {
				Sound.buzz();
				this.notifier.notifyMessage(NotifyTypes.IOEXCEPTION, e);
				break;
			}
			
			if (cmd != null) {
				SimpleQueue<CommandMessage>[] qs = dbuff.getReadQueues();
				int id = cmd.getCommand();
				switch (id) {
				case Constants.LEFT:
				case Constants.RIGHT:
				case Constants.MID:
					qs[id].offer(cmd);
					break;
				case Constants.END:
					qs[DataBuffer.CONN_CMD_INDEX].offer(cmd);
					break;
				}
			}
			Thread.yield();
		}
	}
	
	public void end() {
		this.running = false;
		if (this.in != null) {
			try {
				this.in.close();
			} catch (IOException e) {
				Sound.buzz();
				this.notifier.notifyMessage(NotifyTypes.IOEXCEPTION, e);
			}
		}
	}
}
