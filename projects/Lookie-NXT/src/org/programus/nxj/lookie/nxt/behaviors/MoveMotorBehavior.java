package org.programus.nxj.lookie.nxt.behaviors;

import lejos.nxt.NXTRegulatedMotor;
import lejos.robotics.subsumption.Behavior;

import org.programus.lookie.lib.comm.CommandMessage;
import org.programus.lookie.lib.utils.SimpleQueue;
import org.programus.nxj.lookie.nxt.comm.DataBuffer;

public class MoveMotorBehavior implements Behavior {
	private NXTRegulatedMotor motor;
	private SimpleQueue<CommandMessage> q;
	private boolean processing;
	
	public MoveMotorBehavior(NXTRegulatedMotor motor, int lr) {
		this.motor = motor;
		
		DataBuffer dbuff = DataBuffer.getInstance();
		q = dbuff.getReadQueues()[lr];
	}

	@Override
	public boolean takeControl() {
		return !q.isEmpty();
	}

	@Override
	public void action() {
		this.processing = true;
		while (this.processing && !q.isEmpty()) {
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

	@Override
	public void suppress() {
		this.processing = false;
	}

}
