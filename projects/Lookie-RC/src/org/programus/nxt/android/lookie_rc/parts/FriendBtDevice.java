package org.programus.nxt.android.lookie_rc.parts;

import android.bluetooth.BluetoothDevice;

public class FriendBtDevice {
	private BluetoothDevice device;
	private String friendString;
	
	public FriendBtDevice (BluetoothDevice device) {
		this(device, "/");
	}
	
	public FriendBtDevice (BluetoothDevice device, String delimiter) {
		this(device, "/", "<null>");
	}
	
	public FriendBtDevice (BluetoothDevice device, String delimiter, String nullPrompt) {
		this.device = device;
		if (device != null) {
			this.friendString = device.getName() + delimiter + device.getAddress();
		} else {
			this.friendString = nullPrompt;
		}
	}
	
	public BluetoothDevice getDevice() {
		return this.device;
	}

	@Override
	public String toString() {
		return this.friendString;
	}
	
}
