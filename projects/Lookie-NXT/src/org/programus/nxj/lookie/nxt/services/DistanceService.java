package org.programus.nxj.lookie.nxt.services;

import lejos.nxt.UltrasonicSensor;

import org.programus.lookie.lib.comm.CommandMessage;
import org.programus.lookie.lib.utils.Constants;
import org.programus.lookie.lib.utils.SimpleQueue;
import org.programus.nxj.lookie.nxt.comm.DataBuffer;

public class DistanceService implements Runnable {
	private boolean running = true;
	private UltrasonicSensor sensor;
	
	public DistanceService(UltrasonicSensor sensor) {
		this.sensor = sensor;
	}

	@Override
	public void run() {
		int prevDistance = 0;
		SimpleQueue<CommandMessage> q = DataBuffer.getInstance().getSendQueue();
		while (this.running) {
			int distance = sensor.getDistance();
			if (distance > Constants.DISTANCE_HIDE) {
				distance = Constants.DISTANCE_HIDE + 1;
			}
			if (distance != prevDistance) {
				CommandMessage cmd = new CommandMessage();
				cmd.setCommand(Constants.DISTANCE);
				cmd.setData(distance);
				synchronized (q) {
					q.offer(cmd);
				}
				prevDistance = distance;
			}
			Thread.yield();
		}
	}
	
	public void end() {
		this.running = false;
	}
}
