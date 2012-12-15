package org.programus.nxj.lookie.nxt;

import lejos.nxt.Sound;
import lejos.nxt.comm.BTConnection;
import lejos.nxt.comm.Bluetooth;

public class Lookie {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		while (true) {
			Sound.beep();
			BTConnection conn = Bluetooth.waitForConnection();
			Sound.beepSequenceUp();
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			conn.close();
		}
	}

}
