package org.programus.nxt.android.lookie_camera;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.programus.lookie.lib.comm.CameraCommand;
import org.programus.lookie.lib.utils.Constants;
import org.programus.lookie.lib.utils.MathUtil;
import org.programus.lookie.lib.utils.SimpleQueue;
import org.programus.nxt.android.lookie_camera.comm.CommandReceiver;
import org.programus.nxt.android.lookie_camera.comm.DataBuffer;
import org.programus.nxt.android.lookie_camera.services.MainService;
import org.programus.nxt.android.lookie_camera.utils.Logger;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.ToggleButton;

public class MainActivity extends Activity {
	private final static String TAG = "Main";
	private final static String PREFS_NAME = "Lookie-Eye.Main";
	private final static String KEY_SVC_STATE = "Service.State";
	private final static String KEY_CAM_STATE = "Camera.State";
	private final static String KEY_LOG_TEXT = "Log.Text";
	
	private ToggleButton toggleServiceButton;
	private TextView logText;
	private SurfaceView sv;
	private SurfaceHolder sHolder;
	private Camera camera;
	private BluetoothAdapter btAdapter;
	private Logger logger = Logger.getInstance();
	
	private List<Camera.Size> sizeList;
	private static Comparator<Camera.Size> sizeComparator = new Comparator<Camera.Size>() {
		@Override
		public int compare(Size lhs, Size rhs) {
			return lhs.width - rhs.width;
		}
	};
	
	private SensorManager sensorMgr;
	private Sensor sensor;
	
	private CommandReceiver receiver;
	
	private SimpleQueue<CameraCommand> sendQ = DataBuffer.getInstance().getSendQueue();
	
	private boolean serviceStarted;
	private boolean cameraStarted;
	
	private Handler handler = new UIHandler(this);
	
	private static class UIHandler extends Handler {
		private MainActivity p;
		public UIHandler(MainActivity parent) {
			this.p = parent;
		}
		@Override
		public void handleMessage(Message msg) {
			Bundle b = msg.getData();
			switch (msg.what) {
			case Constants.MSG_WHAT_LOG: {
				String text = b.getString(Constants.KEY_LOG);
				p.logText.append(text);
				p.logText.append(Constants.BR);
				break;
			}
			case Constants.MSG_WHAT_EXCEPTION: {
				IOException e = (IOException) b.getSerializable(Constants.KEY_EXCEPTION);
				String text = b.getString(Constants.KEY_MESSAGE);
				p.logger.log(text);
				p.logger.log(e.getMessage());
				Log.e(TAG, text, e);
				p.endShow();
				break;
			}
			case Constants.MSG_WHAT_CAM_READ: {
				CameraCommand cmd = (CameraCommand) b.getSerializable(Constants.KEY_CAM_CMD);
				switch (cmd.getCommand()) {
				case Constants.CALIBRATE:
					p.startAngleDetection();
					break;
				case Constants.CAMERA:
					p.cameraStarted = true;
					p.startCameraPreview();
					break;
				case Constants.SIZE:
					p.selectPreviewSize(cmd.getFormat());
					break;
				case Constants.END:
					p.endShow();
					break;
				}
				break;
			}
			case Constants.MSG_WHAT_GET_ANGLE: {
				float angle = b.getFloat(Constants.KEY_ANGLE);
				CameraCommand cmd = new CameraCommand();
				cmd.setCommand(Constants.CALIBRATE);
				cmd.setAngle(angle);
				p.sendQ.offer(cmd);
				break;
			}
			}
		}
	}
	
	private OnCheckedChangeListener toggleServiceChangeListener = new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			if (isChecked) {
				if (!serviceStarted && turnOnBt()) {
					beginShow();
				}
			} else {
				if (serviceStarted) {
					endShow();
				}
			}
		}
	};
	
	private SurfaceHolder.Callback shCallback = new SurfaceHolder.Callback() {
		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			Log.d(TAG, "surface destroyed");
			stopCameraPreview();
		}
		
		@Override
		public void surfaceCreated(SurfaceHolder holder) {
			Log.d(TAG, "surface created");
			startCameraPreview();
		}
		
		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
			Log.d(TAG, "surface changed");
			if (holder.getSurface() == null) {
				return;
			}
			
			try {
				stopCameraPreview();
			} catch (Exception e) {
			}
			startCameraPreview();
		}
	};
	
	private SensorEventListener sensorListener = new SensorEventListener() {
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
		}

		@Override
		public void onSensorChanged(SensorEvent event) {
			Bundle b = new Bundle();
			float angle = MathUtil.calculateAngle(event.values);
			b.putFloat(Constants.KEY_ANGLE, angle);
			
			Message msg = new Message();
			msg.what = Constants.MSG_WHAT_GET_ANGLE;
			msg.setData(b);
			handler.sendMessage(msg);
			stopAngleDetection();
		}
	};
	
	private Camera.PreviewCallback camPreviewCallback = new Camera.PreviewCallback() {
		private long prevSentTime;
		private int prevDataLength;
		@Override
		public void onPreviewFrame(byte[] data, Camera camera) {
			long time = System.currentTimeMillis();
			Log.d(TAG, "preview frame:" + time);
			CameraCommand cmd = new CameraCommand();
			Camera.Parameters params = camera.getParameters();
			Camera.Size size = params.getPreviewSize();
			cmd.setCommand(Constants.CAMERA);
			cmd.setFormat(params.getPreviewFormat());
			cmd.setWidth(size.width);
			cmd.setHeight(size.height);
			cmd.setImageData(compressImage(data, camera));
			cmd.setSystemTime(time);
			
			// the time past from last sent
			long dt = time - prevSentTime;
			// the max possible transfer data size during this period
			long maxDataLimit = Constants.MAX_BPMS * dt;
			logger.log(String.format("dt: %d, max: %d, size: %d", dt, maxDataLimit, this.prevDataLength));
			Log.d(TAG, String.format("dt: %d, max: %d, size: %d", dt, maxDataLimit, this.prevDataLength));
			if (this.prevDataLength < maxDataLimit || this.prevDataLength == 0) {
				// last cargo is smaller than the limit
				synchronized(sendQ) {
					Log.d(TAG, String.format("time: %d, len: %d", cmd.getSystemTime(), cmd.getImageData().length));
					sendQ.offer(cmd);
				}
				this.prevDataLength = cmd.getImageData().length;
				this.prevSentTime = time;
			} else {
				Log.d(TAG, "skip a frame");
				logger.log("skip a frame");
			}
		}
	};
	
	private byte[] compressImage(byte[] data, Camera camera) {
		Camera.Parameters params = camera.getParameters();
		Camera.Size size = params.getPreviewSize();
		int format = params.getPreviewFormat();
		YuvImage yuvImage = new YuvImage(data, format, size.width, size.height, null);
		Rect rect = new Rect(0, 0, size.width, size.height);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		yuvImage.compressToJpeg(rect, Constants.COMPRESS_RATE, out);
		byte[] jpg = out.toByteArray();
		
		return compressData(jpg);
	}
	
	private byte[] compressData(byte[] data) {
		return compressDataGZIP(data);
	}
	
	private byte[] compressDataGZIP(byte[] data) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			GZIPOutputStream gzipOut = new GZIPOutputStream(out);
			gzipOut.write(data);
			gzipOut.flush();
			gzipOut.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return out.toByteArray();
	}
	
	private void startAngleDetection() {
		this.sensorMgr.registerListener(sensorListener, this.sensor, SensorManager.SENSOR_DELAY_NORMAL);
	}
	
	private void stopAngleDetection() {
		this.sensorMgr.unregisterListener(sensorListener);
	}
	
	private void startCameraPreview() {
		if (this.cameraStarted) {
			if (this.camera == null) {
				this.camera = Camera.open();
				Camera.Parameters params = this.camera.getParameters();
				this.setupCameraParameters(params);
				this.camera.setParameters(params);
			}
			try {
				camera.setPreviewDisplay(sHolder);
				camera.setPreviewCallback(camPreviewCallback);
				camera.startPreview();
				logger.log("Start cam preview");
			} catch (IOException e) {
				logger.log("Cam Preview failed.");
			}
		}
	}
	
	private void setupCameraParameters(Camera.Parameters params) {
		params.setJpegQuality(Constants.COMPRESS_RATE);
		params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
		
		this.sizeList = params.getSupportedPreviewSizes();
//		for (Iterator<Camera.Size> it = sizeList.iterator(); it.hasNext();) {
//			Camera.Size size = it.next();
//			if (size.width < Constants.SIZE_MIN_WIDTH || size.width > Constants.SIZE_MAX_WIDTH) {
//				it.remove();
//			}
//		}
		Collections.sort(sizeList, sizeComparator);
		Camera.Size minSize = null;
		if (!this.sizeList.isEmpty()) {
			minSize = this.sizeList.get(0);
		}
		if (minSize != null) {
			params.setPreviewSize(Math.max(minSize.width, minSize.height), Math.min(minSize.width, minSize.height));
		}
		
		this.sendAllSizes();
	}
	
	private void sendAllSizes() {
		CameraCommand cmd = new CameraCommand();
		cmd.setCommand(Constants.SIZE);
		cmd.setFormat(this.sizeList.size());
		byte[] sizes = new byte[this.sizeList.size() << 3];
		int i = 0;
		for (Camera.Size size : this.sizeList) {
			int w = Math.max(size.width, size.height);
			int h = Math.min(size.width, size.height);
			this.writeIntIntoByteArray(w, sizes, i << 3);
			this.writeIntIntoByteArray(h, sizes, (i << 3) + 4);
			i++;
		}
		cmd.setImageData(sizes);
		sendQ.offer(cmd);
	}
	
	private void writeIntIntoByteArray(int v, byte[] buff, int offset) {
		for (int i = 0; i < 4; i++) {
			buff[offset + i] = (byte)((v >> i) & 0xff);
		}
	}
	
	private void selectPreviewSize(int index) {
		if (index < 0) {
			index = 0;
		} else if (index >= this.sizeList.size()){
			index = this.sizeList.size() - 1;
		}
		Camera.Size size = this.sizeList.get(index);
		this.camera.stopPreview();
		Camera.Parameters params = this.camera.getParameters();
		params.setPreviewSize(Math.max(size.width, size.height), Math.min(size.width, size.height));
		this.camera.setParameters(params);
		this.camera.startPreview();
	}
	
	private void stopCameraPreview() {
		if (this.camera != null) {
			Camera cam = this.camera;
			this.camera = null;
			synchronized(cam) {
				cam.stopPreview();
				cam.setPreviewCallback(null);
				cam.release();
			}
		}
		this.cameraStarted = false;
	}
	
	private boolean turnOnBt() {
		boolean ret = false;
		if (this.btAdapter == null) {
			this.btAdapter = BluetoothAdapter.getDefaultAdapter();
		}
		
		if (this.btAdapter == null) {
			this.btNotSupported();
		} else {
			ret = this.btAdapter.isEnabled();
			if (!ret) {
				Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				this.startActivityForResult(enableBtIntent, Constants.REQUEST_ENABLE_BT);
			}
		}
		return ret;
	}
	
	private void btNotSupported() {
		this.logger.log("Bluetooth is not suppored on this device.");
		this.logger.log("You cannot use this app");
	}
	
	private void btEnableCancelled() {
		this.logger.log("User cancelled bluetooth enabling.");
		this.toggleServiceButton.setChecked(false);
	}

	@SuppressWarnings("deprecation")
	private void initCompoments() {
		this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		this.toggleServiceButton = (ToggleButton) this.findViewById(R.id.toggleServiceButton);
		this.logText = (TextView) this.findViewById(R.id.logText);
		this.sv = (SurfaceView) this.findViewById(R.id.previewSurface);
		this.sHolder = this.sv.getHolder();
		this.sHolder.addCallback(shCallback);
		this.sHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		
		this.toggleServiceButton.setOnCheckedChangeListener(toggleServiceChangeListener);
		this.logger.setHandler(this.handler);
		
		this.sensorMgr = (SensorManager) this.getSystemService(SENSOR_SERVICE);
		this.sensor = sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		
		SharedPreferences.Editor editor = this.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
		editor.putString(KEY_LOG_TEXT, "");
		editor.commit();
	}
	
	private void beginShow() {
		Intent intent = new Intent(this.getApplicationContext(), MainService.class);
		intent.putExtra(MainService.START_FLAG_KEY, MainService.START_FLAG_START);
		this.startService(intent);
		this.serviceStarted = true;
		this.startThreads();
		logger.log("Service started.");
	}
	
	private void startThreads() {
		if (this.receiver == null) {
			this.receiver = new CommandReceiver(this.handler);
		}
		Thread t = new Thread(this.receiver, "receive command");
		t.setDaemon(true);
		t.start();
	}
	
	private void endShow() { 
		Intent intent = new Intent(this.getApplicationContext(), MainService.class);
		this.stopService(intent);
		this.serviceStarted = false;
		this.stopCameraPreview();
		if (this.receiver != null) {
			this.receiver.end();
		}
		this.toggleServiceButton.setChecked(false);
		logger.log("Service stopped.");
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		this.initCompoments();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == Constants.REQUEST_ENABLE_BT) {
			if (resultCode == RESULT_OK && this.btAdapter.isEnabled()) {
				this.beginShow();
			} else {
				this.btEnableCancelled();
			}
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		SharedPreferences sp = this.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
		this.serviceStarted = sp.getBoolean(KEY_SVC_STATE, false);
		this.cameraStarted = sp.getBoolean(KEY_CAM_STATE, false);
		this.logText.setText(sp.getString(KEY_LOG_TEXT, ""));
		this.toggleServiceButton.setChecked(this.serviceStarted);
	}

	@Override
	protected void onPause() {
		super.onPause();
		this.endShow();
		SharedPreferences.Editor editor = this.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
		editor.putBoolean(KEY_SVC_STATE, serviceStarted);
		editor.putBoolean(KEY_CAM_STATE, cameraStarted);
		editor.putString(KEY_LOG_TEXT, this.logText.getText().toString());
		editor.commit();
	}

}
