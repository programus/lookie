package org.programus.nxj.lookie.nxt.comm;

import java.io.DataOutputStream;
import java.io.IOException;

import lejos.nxt.NXTRegulatedMotor;
import lejos.nxt.Sound;

import org.programus.lookie.lib.comm.CommandMessage;
import org.programus.lookie.lib.utils.Constants;

public class CommandSender implements Runnable {
	private boolean running = true;
	private DataOutputStream out;
	private NXTRegulatedMotor[] motors;
	
	public CommandSender(DataOutputStream out, NXTRegulatedMotor[] motors) {
		this.out = out;
		this.motors = motors;
	}

	@Override
	public void run() {
		int[] pvs = new int[this.motors.length];
		CommandMessage cmd = new CommandMessage();
		while (this.running) {
			for (int i = 0; i < this.motors.length; i++) {
				int v = this.motors[i].getRotationSpeed();
				if (v != pvs[i]) {
					cmd.setCommand(i);
					cmd.setData(-v);
					pvs[i] = v;
					synchronized(out) {
						try {
							cmd.send(out);
							out.flush();
						} catch (IOException e) {
							Sound.buzz();
							e.printStackTrace();
						}
					}
				}
			}
			Thread.yield();
		}
		
		cmd.setCommand(Constants.END);
		cmd.setData(0);
		synchronized(out) {
			try {
				cmd.send(out);
				out.flush();
			} catch (IOException e) {
				Sound.buzz();
				e.printStackTrace();
			}
		}
	}
	
	public void end() {
		this.running = false;
	}
}
