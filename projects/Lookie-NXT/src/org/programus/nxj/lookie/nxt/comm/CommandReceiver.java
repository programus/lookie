package org.programus.nxj.lookie.nxt.comm;

import java.io.DataInputStream;
import java.io.IOException;

import lejos.nxt.LCD;
import lejos.nxt.Sound;

import org.programus.lookie.lib.comm.CommandMessage;
import org.programus.lookie.lib.utils.Constants;
import org.programus.lookie.lib.utils.SimpleQueue;

public class CommandReceiver implements Runnable {
	private boolean running = true;
	private DataInputStream in;
	private DataBuffer dbuff = DataBuffer.getInstance();
	
	public CommandReceiver(DataInputStream in) {
		this.in = in;
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
			}
			
			if (cmd != null) {
				SimpleQueue<CommandMessage>[] qs = dbuff.getReadQueues();
				int id = cmd.getCommand();
				switch (id) {
				case Constants.LEFT:
				case Constants.RIGHT:
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
	}
}
