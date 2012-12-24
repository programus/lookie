package org.programus.nxj.lookie.nxt.comm;

import java.io.DataInputStream;
import java.io.IOException;

import lejos.nxt.LCD;
import lejos.nxt.Sound;

import org.programus.lookie.lib.comm.CommandMessage;
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
				e.printStackTrace();
			}
			
			if (cmd != null) {
				SimpleQueue<CommandMessage>[] qs = dbuff.getReadQueues();
				qs[cmd.getCommand()].offer(cmd);
			}
			Thread.yield();
		}
	}
	
	public void end() {
		this.running = false;
	}
}
