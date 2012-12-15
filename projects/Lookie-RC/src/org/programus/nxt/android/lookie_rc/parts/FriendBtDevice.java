package org.programus.nxt.android.lookie_rc.parts;

import android.bluetooth.BluetoothDevice;

public class FriendBtDevice {
	private BluetoothDevice device;
	private String friendString;
	
	public FriendBtDevice (BluetoothDevice device) {
		this(device, "\n");
	}
	
	public FriendBtDevice (BluetoothDevice device, String delimiter) {
		this.device = device;
		this.friendString = device.getName() + delimiter + device.getAddress();
	}
	
	public BluetoothDevice getDevice() {
		return this.device;
	}

	@Override
	public String toString() {
		return this.friendString;
	}
	
}
