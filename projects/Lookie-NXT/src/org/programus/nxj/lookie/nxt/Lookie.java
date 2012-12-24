package org.programus.nxj.lookie.nxt;

import java.io.DataInputStream;
import java.io.DataOutputStream;

import lejos.nxt.Motor;
import lejos.nxt.NXTRegulatedMotor;
import lejos.nxt.Sound;
import lejos.nxt.comm.BTConnection;
import lejos.nxt.comm.Bluetooth;
import lejos.robotics.subsumption.Arbitrator;
import lejos.robotics.subsumption.Behavior;

import org.programus.nxj.lookie.nxt.behaviors.MoveMotorBehavior;
import org.programus.nxj.lookie.nxt.comm.CommandReceiver;
import org.programus.nxj.lookie.nxt.comm.CommandSender;

public class Lookie {
	
	public final static NXTRegulatedMotor[] wheels = {Motor.C, Motor.B};

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		while (true) {
			Sound.beep();
			BTConnection conn = Bluetooth.waitForConnection();
			Sound.beepSequenceUp();
			DataInputStream in = conn.openDataInputStream();
			DataOutputStream out = conn.openDataOutputStream();
			CommandReceiver receiver = new CommandReceiver(in);
			Thread rcvT = new Thread(receiver, "Rcv");
			rcvT.setDaemon(true);
			rcvT.start();
			
			CommandSender sender = new CommandSender(out, wheels);
			Thread sndT = new Thread(sender, "Snd");
			sndT.setDaemon(true);
			sndT.start();
			
			Behavior[] wheelBehaviors = new Behavior[wheels.length];
			for (int i = 0; i < wheelBehaviors.length; i++) {
				wheelBehaviors[i] = new MoveMotorBehavior(wheels[i], i);
			}
			
			Arbitrator arb = new Arbitrator(wheelBehaviors);
			arb.start();
			conn.close();
		}
	}

}
