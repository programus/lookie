package org.programus.nxj.lookie.nxt.comm;

import java.io.DataOutputStream;
import java.io.IOException;

import lejos.nxt.NXTRegulatedMotor;
import lejos.nxt.Sound;

import org.programus.lookie.lib.comm.CommandMessage;
import org.programus.lookie.lib.utils.Constants;
import org.programus.lookie.lib.utils.SimpleQueue;
import org.programus.nxj.lookie.nxt.utils.Notifiable;
import org.programus.nxj.lookie.nxt.utils.NotifyTypes;

public class CommandSender implements Runnable {
	private boolean running = true;
	private DataOutputStream out;
	private NXTRegulatedMotor[] motors;
	
	private Notifiable notifier;
	
	public CommandSender(DataOutputStream out, NXTRegulatedMotor[] motors, Notifiable notifier) {
		this.out = out;
		this.motors = motors;
		this.notifier = notifier;
	}

	@Override
	public void run() {
		int[] pvs = new int[this.motors.length];
		CommandMessage cmd = new CommandMessage();
		SimpleQueue<CommandMessage> q = DataBuffer.getInstance().getSendQueue();
		while (this.running) {
			while (!q.isEmpty() && this.running) {
				CommandMessage c = q.poll();
				this.sendCommand(c);
			}
			for (int i = 0; i < this.motors.length; i++) {
				int v = this.motors[i].getRotationSpeed();
				if (v != pvs[i]) {
					cmd.setCommand(i);
					cmd.setData(-v);
					pvs[i] = v;
					this.sendCommand(cmd);
				}
			}
			Thread.yield();
		}
		
		cmd.setCommand(Constants.END);
		cmd.setData(0);
		this.sendCommand(cmd);
		
		try {
			out.close();
		} catch (IOException e) {
			Sound.buzz();
			this.notifier.notifyMessage(NotifyTypes.IOEXCEPTION, e);
		}
	}
	
	private void sendCommand(CommandMessage cmd) {
		if (out != null) {
			synchronized(out) {
				try {
					cmd.send(out);
					out.flush();
				} catch (IOException e) {
					Sound.buzz();
					this.notifier.notifyMessage(NotifyTypes.IOEXCEPTION, e);
				}
			}
		}
	}
	
	public void end() {
		this.running = false;
	}
}
