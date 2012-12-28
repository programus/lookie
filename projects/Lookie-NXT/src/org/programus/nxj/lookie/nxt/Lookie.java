package org.programus.nxj.lookie.nxt;

import java.io.DataInputStream;
import java.io.DataOutputStream;

import lejos.nxt.Button;
import lejos.nxt.ButtonListener;
import lejos.nxt.LCD;
import lejos.nxt.Motor;
import lejos.nxt.NXT;
import lejos.nxt.NXTRegulatedMotor;
import lejos.nxt.SensorPort;
import lejos.nxt.Sound;
import lejos.nxt.TouchSensor;
import lejos.nxt.UltrasonicSensor;
import lejos.nxt.comm.BTConnection;
import lejos.nxt.comm.Bluetooth;

import org.programus.lookie.lib.comm.CommandMessage;
import org.programus.lookie.lib.utils.Constants;
import org.programus.lookie.lib.utils.SimpleQueue;
import org.programus.nxj.lookie.nxt.comm.CommandReceiver;
import org.programus.nxj.lookie.nxt.comm.CommandSender;
import org.programus.nxj.lookie.nxt.comm.DataBuffer;
import org.programus.nxj.lookie.nxt.services.CameraMotorService;
import org.programus.nxj.lookie.nxt.services.InitCalibrateService;
import org.programus.nxj.lookie.nxt.services.MoveMotorService;
import org.programus.nxj.lookie.nxt.utils.Notifiable;
import org.programus.nxj.lookie.nxt.utils.NotifyTypes;

public class Lookie {
	
	public final static NXTRegulatedMotor[] wheels = {Motor.C, Motor.B};
	public final static NXTRegulatedMotor head = Motor.A;
	public final static TouchSensor stopSensor = new TouchSensor(SensorPort.S2);
	public final static UltrasonicSensor distanceSensor = new UltrasonicSensor(SensorPort.S3);
	
	public final static int MIN_DISTANCE = 10;
	
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
	
	private static Thread initCalibrate() {
		Thread t = new Thread(new InitCalibrateService(head, stopSensor, notifier), "init calibrate");
		t.setDaemon(true);
		t.start();
		return t;
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
			
			MoveMotorService[] wheelServices = new MoveMotorService[wheels.length];
			for (int i = 0; i < wheelServices.length; i++) {
				wheelServices[i] = new MoveMotorService(wheels[i], i);
				Thread t = new Thread(wheelServices[i], "Wheel Motor - " + i);
				t.setDaemon(true);
				t.start();
			}
			
			CameraMotorService cameraService = new CameraMotorService(head, Constants.MID);
			Thread tc = new Thread(cameraService, "Cam head");
			tc.setDaemon(true);
			tc.start();
			
			while (running) {
				while (criticalQ.isEmpty() && running) {
					if (distanceSensor.getDistance() < MIN_DISTANCE) {
						Sound.beep();
					}
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
				for (MoveMotorService s : wheelServices) {
					s.end();
				}
				cameraService.end();
				Thread.yield();
			}
			
			conn.close();
			LCD.clear();
			
			// I hope this robot can wait connecting again once disconnected. 
			// But it not work well now.
			// So shutdown now to save battery.
			// Don't mind the while loop, it just for the future, if there is a future. :-P
			Sound.beepSequence();
			Thread t = initCalibrate();
			try {
				t.join();
			} catch (InterruptedException e) {
			}
			NXT.shutDown();
		}
	}

}
