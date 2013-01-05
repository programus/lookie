package org.programus.nxj.lookie.nxt.services;

import lejos.nxt.NXTRegulatedMotor;
import lejos.nxt.Sound;
import lejos.nxt.TouchSensor;

import org.programus.nxj.lookie.nxt.utils.Notifiable;
import org.programus.nxj.lookie.nxt.utils.NotifyTypes;

public class InitCalibrateService implements Runnable {
	private NXTRegulatedMotor motor;
	private TouchSensor[] stopSensors;
	private Notifiable notifier;
	private final static int calibrateSpeed = 200;
	
	public InitCalibrateService(NXTRegulatedMotor motor, TouchSensor[] stopSensors, Notifiable notifier) {
		this.motor = motor;
		this.stopSensors = stopSensors;
		this.notifier = notifier;
	}
	
	private boolean isAnySensorPressed() {
		boolean ret = false;
		for (TouchSensor sensor : this.stopSensors) {
			if (sensor.isPressed()) {
				ret = true;
				break;
			}
		}
		return ret;
	}
	
//	private boolean isAllSensorPressed() {
//		boolean ret = true;
//		for (TouchSensor sensor: this.stopSensors) {
//			if (!sensor.isPressed()) {
//				ret = false;
//				break;
//			}
//		}
//		return ret;
//	}
//	
	@Override
	public void run() {
		while (!this.isAnySensorPressed()) {
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
