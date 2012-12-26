package org.programus.nxj.lookie.nxt.services;

import lejos.nxt.NXTRegulatedMotor;
import lejos.nxt.Sound;
import lejos.nxt.TouchSensor;

import org.programus.nxj.lookie.nxt.utils.Notifiable;
import org.programus.nxj.lookie.nxt.utils.NotifyTypes;

public class InitCalibrateService implements Runnable {
	private NXTRegulatedMotor motor;
	private TouchSensor stopSensor;
	private Notifiable notifier;
	private final static int calibrateSpeed = 500;
	
	public InitCalibrateService(NXTRegulatedMotor motor, TouchSensor stopSensor, Notifiable notifier) {
		this.motor = motor;
		this.stopSensor = stopSensor;
		this.notifier = notifier;
	}
	
	@Override
	public void run() {
		while (!this.stopSensor.isPressed()) {
			if (!this.motor.isMoving()) {
				this.motor.setSpeed(calibrateSpeed);
				this.motor.backward();
			} else {
				Thread.yield();
			}
		}
		this.motor.stop();
		this.motor.resetTachoCount();
		Sound.beep();
		this.notifier.notifyMessage(NotifyTypes.FINISHED_INIT, null);
	}

}
