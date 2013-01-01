package org.programus.nxt.android.lookie_camera.services;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;

import org.programus.nxt.android.lookie_camera.activites.MainActivity;
import org.programus.nxt.android.lookie_camera.comm.BluetoothAccept;
import org.programus.nxt.android.lookie_camera.comm.CameraCommandReceiver;
import org.programus.nxt.android.lookie_camera.comm.CameraCommandSender;
import org.programus.nxt.android.lookie_camera.comm.DataBuffer;
import org.programus.nxt.android.lookie_camera.utils.Logger;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

public class MainService extends Service {
	private BluetoothAdapter btAdapter;
	private BluetoothAccept btAccept;
	
	private boolean started;
	
	private int notificationId = 5;
	private Notification notification;
	
	private Logger logger = Logger.getInstance();
	
	private CameraCommandReceiver receiver;
	private CameraCommandSender sender;
	
	public final static String START_FLAG_KEY = "Start.Flag";
	public final static int START_FLAG_START = 0;
	public final static int START_FLAG_BT_CONNECTED = 1;
	
	private void startBtListeningServer() {
		if (!started) {
			if (this.btAdapter == null) {
				this.btAdapter = BluetoothAdapter.getDefaultAdapter();
			}
			if (btAdapter == null) {
				logger.log("Bluetooth is not supported.");
			} else {
				this.btAccept = new BluetoothAccept(this.btAdapter, this.getApplicationContext());
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
	
	private void runForground() {
		if (this.notification == null) {
			Intent intent = new Intent(this, MainActivity.class);
			PendingIntent pintent = PendingIntent.getActivity(this, 0, intent, 0);
			notification = new NotificationCompat.Builder(this.getApplicationContext())
				.setContentTitle("Lookie")
				.setContentText("Bluetooth Camera")
				.setSmallIcon(android.R.drawable.ic_menu_camera)
				.setContentIntent(pintent)
				.build();
		}
		this.startForeground(notificationId, notification);
	}
	
	private void processEvent(int startFlag) {
		switch (startFlag) {
		case START_FLAG_START:
			this.runForground();
			this.startBtListeningServer();
			this.started = true;
			break;
		case START_FLAG_BT_CONNECTED:
			logger.log("Bluetooth connected.");
			try {
				this.startThreads(this.btAccept.getSocket());
			} catch (StreamCorruptedException e) {
				this.logger.log("Start sender and receiver failed.");
				e.printStackTrace();
			} catch (IOException e) {
				this.logger.log("Start sender and receiver failed.");
				e.printStackTrace();
			}
			break;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();
	}

	@Override
	public void onDestroy() {
		this.started = false;
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
		logger.log("Stopped listen.");
		
		if (this.receiver != null) {
			this.receiver.end();
		}
		if (this.sender != null) {
			this.sender.end();
		}
		Thread.yield();
		DataBuffer.getInstance().getReadQueue().clear();
		DataBuffer.getInstance().getSendQueue().clear();
		super.onDestroy();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		
		int startFlag = intent.getIntExtra(START_FLAG_KEY, START_FLAG_START);
		this.processEvent(startFlag);
		
		return START_STICKY;
	}

}
