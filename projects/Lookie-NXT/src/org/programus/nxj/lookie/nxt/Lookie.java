package org.programus.nxj.lookie.nxt;

import java.io.DataInputStream;
import java.io.DataOutputStream;

import lejos.nxt.Button;
import lejos.nxt.ButtonListener;
import lejos.nxt.LCD;
import lejos.nxt.Motor;
import lejos.nxt.NXTRegulatedMotor;
import lejos.nxt.SensorPort;
import lejos.nxt.Sound;
import lejos.nxt.TouchSensor;
import lejos.nxt.comm.BTConnection;
import lejos.nxt.comm.Bluetooth;

import org.programus.lookie.lib.comm.CommandMessage;
import org.programus.lookie.lib.utils.Constants;
import org.programus.lookie.lib.utils.SimpleQueue;
import org.programus.nxj.lookie.nxt.comm.CommandReceiver;
import org.programus.nxj.lookie.nxt.comm.CommandSender;
import org.programus.nxj.lookie.nxt.comm.DataBuffer;
import org.programus.nxj.lookie.nxt.services.InitCalibrateService;
import org.programus.nxj.lookie.nxt.services.MoveMotorService;
import org.programus.nxj.lookie.nxt.utils.Notifiable;
import org.programus.nxj.lookie.nxt.utils.NotifyTypes;

public class Lookie {
	
	public final static NXTRegulatedMotor[] wheels = {Motor.C, Motor.B};
	public final static NXTRegulatedMotor head = Motor.A;
	public final static TouchSensor stopSensor = new TouchSensor(SensorPort.S2);
	
	private static boolean running = true;
	private static SimpleQueue<CommandMessage> criticalQ = DataBuffer.getInstance().getReadQueues()[DataBuffer.CONN_CMD_INDEX];
	
	private static Notifiable notifier = new Notifiable() {
		@Override
		public boolean notifyMessage(int type, Object data) {
			switch (type) {
			case NotifyTypes.FINISHED_INIT: {
				CommandMessage cmd = new CommandMessage();
				cmd.setCommand(Constants.CALIBRATE);
				cmd.setData(0);
				SimpleQueue<CommandMessage> q = DataBuffer.getInstance().getSendQueue();
				q.offer(cmd);
				break;
			}
			case NotifyTypes.IOEXCEPTION: {
				CommandMessage cmd = new CommandMessage();
				cmd.setCommand(Constants.END);
				cmd.setData(0);
				criticalQ.offer(cmd);
				break;
			}
			}
			return true;
		}
	};
	
	private static void initCalibrate() {
		Thread t = new Thread(new InitCalibrateService(head, stopSensor, notifier), "init calibrate");
		t.setDaemon(true);
		t.start();
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// Press ESC to exit.
		Button.ESCAPE.addButtonListener(new ButtonListener() {
			@Override
			public void buttonPressed(Button b) {
				running = false;
			}
			@Override
			public void buttonReleased(Button b) {
			}
		});
		
		while (running) {
			Sound.beep();
			initCalibrate();
			LCD.drawString("BT Conn waiting...     ", 0, 0);
			BTConnection conn = Bluetooth.waitForConnection();
			LCD.drawString("BT Connected.          ", 0, 1);
			Sound.beepSequenceUp();
			DataInputStream in = conn.openDataInputStream();
			DataOutputStream out = conn.openDataOutputStream();
			CommandReceiver receiver = new CommandReceiver(in, notifier);
			Thread rcvT = new Thread(receiver, "Rcv");
			rcvT.setDaemon(true);
			rcvT.start();
			
			CommandSender sender = new CommandSender(out, wheels, notifier);
			Thread sndT = new Thread(sender, "Snd");
			sndT.setDaemon(true);
			sndT.start();
			
			Runnable[] wheelServices = new Runnable[wheels.length];
			for (int i = 0; i < wheelServices.length; i++) {
				wheelServices[i] = new MoveMotorService(wheels[i], i);
				Thread t = new Thread(wheelServices[i], "Wheel Motor - " + i);
				t.setDaemon(true);
				t.start();
			}
			
			while (running) {
				while (criticalQ.isEmpty() && running) {
					Thread.yield();
				}
				
				if (!criticalQ.isEmpty()) {
					CommandMessage cmd = criticalQ.poll();
					if (cmd.getCommand() == Constants.END) {
						criticalQ.clear();
						break;
					}
				}
			}
			
			if (!running) {
				sender.end();
				receiver.end();
				Thread.yield();
			}
			
			conn.close();
			LCD.clear();
		}
	}

}
