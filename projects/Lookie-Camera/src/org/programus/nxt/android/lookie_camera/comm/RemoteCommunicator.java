package org.programus.nxt.android.lookie_camera.comm;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;

import org.programus.nxt.android.lookie_camera.utils.Logger;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothSocket;

public class RemoteCommunicator {
	private BluetoothAdapter btAdapter;
	private BluetoothAccept btAccept;

	private Logger logger = Logger.getInstance();
	
	private boolean started;
	
	private CameraCommandReceiver receiver;
	private CameraCommandSender sender;
	
	private BluetoothAccept.OnConnectedCallback onConnectedCallback = new BluetoothAccept.OnConnectedCallback() {
		@Override
		public void onConnected(BluetoothSocket socket, BluetoothAccept accept) {
			try {
				startThreads(socket);
			} catch (StreamCorruptedException e) {
				logger.log("Start sender and receiver failed.");
				e.printStackTrace();
			} catch (IOException e) {
				logger.log("Start sender and receiver failed.");
				e.printStackTrace();
			}
		}
	};
	
	private void startBtListeningServer() {
		if (!started) {
			if (this.btAdapter == null) {
				this.btAdapter = BluetoothAdapter.getDefaultAdapter();
			}
			if (btAdapter == null) {
				logger.log("Bluetooth is not supported.");
			} else {
				this.btAccept = new BluetoothAccept(this.btAdapter);
				this.btAccept.setOnConnectedCallback(onConnectedCallback);
				Thread t = new Thread(this.btAccept, "BT Listen");
				t.setDaemon(true);
				t.start();
			}
		}
	}
	
	private void startThreads(BluetoothSocket socket) throws StreamCorruptedException, IOException {
		if (this.receiver == null) {
			this.receiver = new CameraCommandReceiver(new ObjectInputStream(socket.getInputStream()));
		}
		Thread tr = new Thread(this.receiver, "Data receiver");
		tr.setDaemon(true);
		tr.start();
		
		if (this.sender == null) {
			this.sender = new CameraCommandSender(new ObjectOutputStream(socket.getOutputStream()));
		}
		Thread ts = new Thread(this.sender, "Data sender");
		ts.setDaemon(true);
		ts.start();
	}
	
	public void start() {
		if (!this.started) {
			this.startBtListeningServer();
			this.started = true;
		}
	}
	
	public void end() {
		if (this.started) {
			this.started = false;
			
			if (this.receiver != null) {
				this.receiver.end();
				this.receiver = null;
			}
			if (this.sender != null) {
				this.sender.end();
				this.sender = null;
			}
			Thread.yield();
			
			if (this.btAccept.getSocket() != null) {
				try {
					this.btAccept.getSocket().close();
					logger.log("Connection closed.");
				} catch (IOException e) {
					logger.log("Close socket error: " + e.getMessage());
					e.printStackTrace();
				}
			}
			this.btAccept.cancel();
			this.btAccept = null;
			logger.log("Stopped listen.");
			DataBuffer.getInstance().getReadQueue().clear();
			DataBuffer.getInstance().getSendQueue().clear();
		}
	}
	
	public boolean isStarted() {
		return this.started;
	}
}
