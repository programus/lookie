package org.programus.nxj.lookie.nxt.services;

import lejos.nxt.NXTRegulatedMotor;

import org.programus.lookie.lib.comm.CommandMessage;
import org.programus.lookie.lib.utils.SimpleQueue;
import org.programus.nxj.lookie.nxt.comm.DataBuffer;

public class MoveMotorService implements Runnable {
	private NXTRegulatedMotor motor;
	private SimpleQueue<CommandMessage> q;
	
	private boolean running = true;

	public MoveMotorService(NXTRegulatedMotor motor, int lr) {
		this.motor = motor;
		
		DataBuffer dbuff = DataBuffer.getInstance();
		q = dbuff.getReadQueues()[lr];
	}
	
	@Override
	public void run() {
		while(this.running) {
			while (this.q.isEmpty()) {
				Thread.yield();
			}
			CommandMessage cmd;
			synchronized(q) {
				cmd = q.poll();
			}
			float speed = cmd.getData();
			boolean forward = speed < 0;
			speed = Math.abs(speed);
			synchronized(this.motor) {
				if (speed > 0.000001) {
					this.motor.setSpeed(speed);
					if (forward) {
						this.motor.forward();
					} else {
						this.motor.backward();
					}
				} else {
					this.motor.stop();
				}
			}
			Thread.yield();
		}
	}
	
	public void end() {
		this.running = false;
	}

}
