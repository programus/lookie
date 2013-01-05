package org.programus.nxj.lookie.nxt.services;

import lejos.nxt.NXTRegulatedMotor;

import org.programus.lookie.lib.comm.CommandMessage;
import org.programus.lookie.lib.utils.SimpleQueue;
import org.programus.nxj.lookie.nxt.comm.DataBuffer;

public class CameraMotorService implements Runnable {
	private NXTRegulatedMotor motor;
	private SimpleQueue<CommandMessage> q;
	
	private final static int ANGLE_RATE = 5;
	private final static int MIN_R_ANGLE = 0;
	private final static int MAX_R_ANGLE = 450;
	private final static int SPEED_RATE = 50;
	
	private boolean running = true;

	public CameraMotorService(NXTRegulatedMotor motor, int lr) {
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
			float angle = cmd.getData();
			int rotateAngle = (int) (angle * ANGLE_RATE);
			if (rotateAngle < MIN_R_ANGLE) {
				rotateAngle = MIN_R_ANGLE;
			} else if (rotateAngle > MAX_R_ANGLE) {
				rotateAngle = MAX_R_ANGLE;
			}
			int currAngle = this.motor.getTachoCount();
			int da = Math.abs(rotateAngle - currAngle);
			if (da > 2) {
				synchronized(this.motor) {
//					this.motor.setSpeed(this.motor.getMaxSpeed());
					this.motor.setSpeed(da * SPEED_RATE);
					this.motor.rotateTo(rotateAngle, true);
				}
			} else {
				synchronized(this.motor) {
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
