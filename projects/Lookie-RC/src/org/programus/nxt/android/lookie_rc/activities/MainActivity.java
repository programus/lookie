package org.programus.nxt.android.lookie_rc.activities;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.programus.lookie.lib.comm.CameraCommand;
import org.programus.lookie.lib.comm.CommandMessage;
import org.programus.lookie.lib.utils.Constants;
import org.programus.lookie.lib.utils.MathUtil;
import org.programus.lookie.lib.utils.SimpleQueue;
import org.programus.nxt.android.lookie_rc.R;
import org.programus.nxt.android.lookie_rc.comm.CameraBtCommunicator;
import org.programus.nxt.android.lookie_rc.comm.DataBuffer;
import org.programus.nxt.android.lookie_rc.comm.NXTData;
import org.programus.nxt.android.lookie_rc.comm.RobotBtCommunicator;
import org.programus.nxt.android.lookie_rc.parts.FriendBtDevice;
import org.programus.nxt.android.lookie_rc.widgets.SpeedBar;
import org.programus.nxt.android.lookie_rc.widgets.SpeedBar.OnValueChangedListener;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

public class MainActivity extends Activity {
	private final static String TAG = "Main";
	
	private final static String PREF_NAME = "Lookie-RC";
	private final static String KEY_CAM_SELECTION = "CamSel";
	private final static int THRESHOLD = 10;
	
	private Button connectButton;
	private Button exitControl;
	private TextView logText;
	private Spinner deviceSelection;
	private Spinner previewSizes;
	private ToggleButton toggleLight;
	
	private SpeedBar[] speeds = new SpeedBar[2];
	private float[] speedTargets = new float[2];
	private DataBuffer dbuff = DataBuffer.getInstance();
	
	private View setupView;
	private View controlView;
	
	private RobotBtCommunicator nxtCommunicator;
	private CameraBtCommunicator camCommunicator;
	private BluetoothAdapter btAdapter;
	
	private SurfaceView cameraSv;
	private SurfaceHolder cameraSh;
	private int svBgColor;
	
	private SensorManager sensorMgr;
	private Sensor sensor;
	
	private float zeroAngle;
	
	private Rect dirtyRect;
	
	private boolean connectedRobot;
	private boolean connectedCamera;
	
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
			case Constants.MSG_WHAT_ROBOT_CONNECT: {
				int status = b.getInt(Constants.KEY_ROBOT_CONNECT_STATUS);
				p.logText.append(status == Constants.CONN_STATUS_CONNECTED ? "NXT Connected!" : "NXT Connected failed.");
				p.logText.append(Constants.BR);
				p.robotConnected(status == Constants.CONN_STATUS_CONNECTED);
				if (status == Constants.CONN_STATUS_CONNECTED) {
					p.connectCamera();
				}
				break;
			}
			case Constants.MSG_WHAT_CAMERA_CONNECT: {
				int status = b.getInt(Constants.KEY_CAMERA_CONNECT_STATUS);
				String deviceString = b.getString(Constants.KEY_CAMERA_DEVICE);
				p.logText.append(status == Constants.CONN_STATUS_CONNECTED ? "Camera Connected!" : "Camera Connected failed.");
				p.logText.append(Constants.BR);
				p.cameraConnected(status == Constants.CONN_STATUS_CONNECTED);
				@SuppressWarnings("unchecked")
				List<FriendBtDevice> deviceList = (List<FriendBtDevice>) p.deviceSelection.getTag();
				int index = 0;
				for (FriendBtDevice device : deviceList) {
					if (device.toString().equals(deviceString)) {
						p.deviceSelection.setSelection(index);
						p.saveCamSelection(index);
						break;
					}
					index++;
				}
				break;
			}
			case Constants.MSG_WHAT_EXCEPTION: {
				IOException e = (IOException) b.getSerializable(Constants.KEY_EXCEPTION);
				String text = b.getString(Constants.KEY_MESSAGE);
				Log.e(TAG, text, e);
				try {
					p.logText.append(text);
					p.logText.append(Constants.BR);
					p.logText.append(e.getMessage());
				} catch (Exception ex) {
					ex.printStackTrace();
				}
				p.disconnectCamera();
				p.disconnectRobot();
				break;
			}
			case Constants.MSG_WHAT_DATA_READ: {
				NXTData data = (NXTData) b.getSerializable(Constants.KEY_NXT_DATA);
				int dir = data.getDir();
				float speed = data.getSpeed();
				Log.d(TAG, String.format("Actual: dir: %d, v: %f", dir, speed));
				switch (dir) {
				case Constants.LEFT:
				case Constants.RIGHT:
					p.speeds[dir].setActualValue(speed);
					p.speeds[dir].invalidate();
					break;
				case Constants.END:
					p.disconnectCamera();
					p.disconnectRobot();
					break;
				case Constants.CALIBRATE: {
					CameraCommand cmd = new CameraCommand();
					cmd.setCommand(Constants.CALIBRATE);
					p.camCommunicator.sendCommand(cmd);
					break;
				}
				default:
					Log.w(TAG, "Wrong Command");
					break;
				}
				break;
			}
			case Constants.MSG_WHAT_CAM_READ: {
				CameraCommand cmd = (CameraCommand) b.getSerializable(Constants.KEY_CAM_CMD);
				switch (cmd.getCommand()) {
				case Constants.CALIBRATE: {
					p.zeroAngle = cmd.getAngle();
					p.startDetectAngle();
					CameraCommand command = new CameraCommand();
					command.setCommand(Constants.CAMERA);
					p.camCommunicator.sendCommand(command);
					break;
				}
				case Constants.CAMERA:
					byte[] imageData = cmd.getImageData();
					Bitmap bmp = p.extractBitmap(imageData, cmd.getWidth(), cmd.getHeight(), cmd.getFormat());
					p.displayCameraPreview(bmp, cmd.getWidth(), cmd.getHeight());
					break;
				case Constants.SIZE:
					p.fillSizesIntoSpinner(cmd);
					break;
				case Constants.END:
					p.disconnectCamera();
					p.disconnectRobot();
					break;
				}
			}
			}
		}
	}
	
	private static class SpeedBarValueChangedListener implements OnValueChangedListener {
		private SimpleQueue<CommandMessage> q;
		private int command;
		
		public SpeedBarValueChangedListener(SimpleQueue<CommandMessage> q, int cmd) {
			this.q = q;
			this.command = cmd;
		}
		
		@Override
		public void onTargetValueChanged(SpeedBar sb, float oldValue, float newValue, float rawValue) {
			if (oldValue != newValue) {
				CommandMessage cmd = new CommandMessage();
				cmd.setCommand(command);
				cmd.setData(newValue);
				this.q.offer(cmd);
			}
		}

		@Override
		public void onActualValueChanged(SpeedBar sb, float oldValue, float newValue, float rawValue) {
		}
		
	}

	private OnClickListener connectClickListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			if (turnOnBt()) {
//				connectDevices();
				connectRobot();
			}
		}
	};
	
	private OnClickListener exitControlClickListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			disconnectRobot();
			disconnectCamera();
		}
	};
	
	private OnItemSelectedListener camSelectedListener = new OnItemSelectedListener() {
		@Override
		public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
			saveCamSelection(pos);
		}
		
		@Override
		public void onNothingSelected(AdapterView<?> parent) {
		}
	};
	
	private OnItemSelectedListener sizeSelectedListener = new OnItemSelectedListener() {
		@Override
		public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
			changePreviewSize(pos);
		}
		
		@Override
		public void onNothingSelected(AdapterView<?> parent) {
		}
	};
	
	private OnCheckedChangeListener toggleLightListener = new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			CameraCommand cmd = new CameraCommand();
			cmd.setCommand(Constants.LIGHT);
			cmd.setFormat(isChecked ? 1 : 0);
			camCommunicator.sendCommand(cmd);
		}
	};
	
	private OnTouchListener speedControlTouchListener = new OnTouchListener() {
		private int[] pids = {Constants.INVALID_POINTER_ID, Constants.INVALID_POINTER_ID};
		private PointF[] downPs = {new PointF(), new PointF()};
		private int pCount = 0;
		private final static int MAX_COUNT = 2;
		
		private int getIndexFromPid(int pid) {
			int index = -1;
			for (int i = 0; i < pids.length; i++) {
				if (pids[i] == pid) {
					index = i;
					break;
				}
			}
			return index;
		}
		
		@Override
		public boolean onTouch(View v, MotionEvent event) {
			Log.v(TAG, String.format("Touched: %d points/ evt: %s", event.getPointerCount(), event.toString()));
			int action = event.getAction();
			switch (action & MotionEvent.ACTION_MASK) {
			case MotionEvent.ACTION_DOWN: 
			case MotionEvent.ACTION_POINTER_DOWN: {
				if (pCount < MAX_COUNT) {
					final int i = (action & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
					final int pid = event.getPointerId(i);
					Log.d(TAG, String.format("Down Pointer index: %d / id: %d", i, pid));

					final float x = event.getX(i);
					final float y = event.getY(i);
					final int index = x < ((float) v.getWidth() / 2) ? Constants.LEFT
							: Constants.RIGHT;
					if (pids[index] < 0) {
						pids[index] = pid;
						downPs[index].x = x;
						downPs[index].y = y;
						pCount++;
						storeSpeedTargetPx(index, pCount);
					}
				}
				break;
			}
			case MotionEvent.ACTION_CANCEL:
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_POINTER_UP:
			{
				final int i = (action & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
				final int pid = event.getPointerId(i);
				Log.d(TAG, String.format("Up Pointer index: %d / id: %d", i, pid));
				final int index = this.getIndexFromPid(pid);
				if (index >= 0) {
					pids[index] = Constants.INVALID_POINTER_ID;
					pCount--;
					releaseSpeedTarget(index, pCount);
				}
				break;
			}
			case MotionEvent.ACTION_MOVE:
			{
				for (int j = 0; j < pids.length; j++) {
					final int pid = pids[j];
					final int i = event.findPointerIndex(pid);
					Log.d(TAG, String.format("Move Pointer index: %d / id: %d", i, pid));
					if (i >= 0) {
						final float y = event.getY(i);
						final int index = this.getIndexFromPid(pid);
						final PointF p = downPs[index];
						float dy = y - p.y;
						offsetSpeedTargetPx(index, pCount, dy);
					}
				}
				break;
			}
			}
			return true;
		}
	};
	
	private SensorEventListener angleSensorListener = new SensorEventListener() {
		SimpleQueue<CommandMessage> q = DataBuffer.getInstance().getSendQueues()[Constants.MID];
		float prevDa;
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
		}
		@Override
		public void onSensorChanged(SensorEvent event) {
			float angle = MathUtil.calculateAngle(event.values);
			float da = angle - zeroAngle;
			if (da < 0) {
				da += 360;
			}
			if (Math.abs(da - prevDa) > THRESHOLD) {
				CommandMessage cmd = new CommandMessage();
				cmd.setCommand(Constants.MID);
				cmd.setData(da);
				q.offer(cmd);
				prevDa = da;
			}
		}
	};
	
	private void startDetectAngle() {
		this.sensorMgr.registerListener(angleSensorListener, sensor, SensorManager.SENSOR_DELAY_UI);
		Log.d(TAG, "Started sensor listening");
	}
	
	private void stopDetectAngle() {
		this.sensorMgr.unregisterListener(angleSensorListener);
	}
	
	private void storeSpeedTargetPx(int index, int count) {
		this.speedTargets[index] = this.speeds[index].getTargetPxValue();
	}
	
	private void releaseSpeedTarget(int index, int count) {
		if (count == 1) {
			this.speeds[index].setTargetPxValue(this.speeds[index == Constants.LEFT ? Constants.RIGHT : Constants.LEFT].getTargetPxValue());
			this.speeds[index].invalidate();
		} else if (count == 0) {
			for (SpeedBar sb : this.speeds) {
				sb.setTargetPxValue(0);
				sb.invalidate();
			}
		} else {
			this.speeds[index].setTargetPxValue(0);
			this.speeds[index].invalidate();
		}
	}
	
	private void offsetSpeedTargetPx(int index, int count, float dv) {
		float value = this.speedTargets[index] - dv;
		if (count == 1) {
			for (SpeedBar sb : this.speeds) {
				sb.setTargetPxValue(value);
				sb.invalidate();
			}
		} else {
			this.speeds[index].setTargetPxValue(value);
			this.speeds[index].invalidate();
		}
	}
//	
//	private void connectDevices() {
//		connectRobot();
//		connectCamera();
//	}
	
	private void connectCamera() {
		if (camCommunicator == null) {
			this.camCommunicator = new CameraBtCommunicator(this.btAdapter, this.handler);
		}
		logText.append("Connecting to Camera...");
		logText.append(Constants.BR);
		int index = this.deviceSelection.getSelectedItemPosition();
		@SuppressWarnings("unchecked")
		List<FriendBtDevice> deviceList = (List<FriendBtDevice>) this.deviceSelection.getTag();
		this.camCommunicator.connect(deviceList.get(index), deviceList);
	}
	
	private void disconnectCamera() {
		if (this.connectedCamera) {
			Log.d(TAG, "Disconnecting from Camera...");
			logText.append("Disconnecting from Camera...");
			logText.append(Constants.BR);
			try {
				this.camCommunicator.end();
			} catch (IOException e) {
				logText.append(e.getMessage());
				logText.append(Constants.BR);
				Log.w(TAG, "disconnect failed.", e);
			}
			logText.append("Camera disconnected.");
			logText.append(Constants.BR);
			Log.d(TAG, "Camera disconnected");
			this.cameraConnected(false);
		}
		this.devicesConnected();
	}
	
	private void connectRobot() {
		if (nxtCommunicator == null) {
			nxtCommunicator = new RobotBtCommunicator(handler);
		}
		logText.append("Connecting to NXT...");
		logText.append(Constants.BR);
		nxtCommunicator.connect();
	}
	
	private void disconnectRobot() {
		if (this.connectedRobot) {
			Log.d(TAG, "Disconnecting from NXT...");
			logText.append("Disconnecting from NXT...");
			logText.append(Constants.BR);
			try {
				this.nxtCommunicator.end();
			} catch (IOException e) {
				logText.append(e.getMessage());
				logText.append(Constants.BR);
				Log.w(TAG, "disconnect failed", e);
			}
			Log.d(TAG, "NXT disconnected");
			logText.append("NXT disconnected.");
			logText.append(Constants.BR);
			this.robotConnected(false);
		}
		this.devicesConnected();
	}
	
	private void devicesConnected() {
		if (this.connectedRobot && this.connectedCamera) {
			this.setupView.setVisibility(View.GONE);
			this.controlView.setVisibility(View.VISIBLE);
			this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		} else {
			this.dirtyRect = null;
			this.setupView.setVisibility(View.VISIBLE);
			this.controlView.setVisibility(View.GONE);
			this.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			this.stopDetectAngle();
		}
	}
	
	private void robotConnected(boolean isConnected) {
		this.connectedRobot = isConnected;
		this.devicesConnected();
	}
	
	private void cameraConnected(boolean isConnected) {
		this.connectedCamera = isConnected;
		this.devicesConnected();
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
	
	private void initComponents() {
		this.connectButton = (Button) this.findViewById(R.id.connectButton);
		this.exitControl = (Button) this.findViewById(R.id.exitControl);
		this.logText = (TextView) this.findViewById(R.id.logText);
		this.deviceSelection = (Spinner) this.findViewById(R.id.camSelection);
		this.previewSizes = (Spinner) this.findViewById(R.id.previewSizes);
		this.toggleLight = (ToggleButton) this.findViewById(R.id.toggleLight);
		
		this.deviceSelection.setOnItemSelectedListener(camSelectedListener);
		this.previewSizes.setOnItemSelectedListener(sizeSelectedListener);
		this.toggleLight.setOnCheckedChangeListener(toggleLightListener);
		
		if (this.turnOnBt()) {
			this.setupDevicesForSpinner();
		}
		
		this.speeds[Constants.LEFT] = (SpeedBar) this.findViewById(R.id.leftSpeedBar);
		this.speeds[Constants.RIGHT] = (SpeedBar) this.findViewById(R.id.rightSpeedBar);
		this.setupView = this.findViewById(R.id.setupView);
		this.controlView = this.findViewById(R.id.controlView);
		
		this.cameraSv = (SurfaceView) this.findViewById(R.id.cameraSurface);
		this.cameraSh = this.cameraSv.getHolder();
		this.svBgColor = this.getResources().getColor(android.R.color.background_light);
		
		this.sensorMgr = (SensorManager) this.getSystemService(SENSOR_SERVICE);
		this.sensor = sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		
		this.devicesConnected();
		
		SimpleQueue<CommandMessage>[] qs = dbuff.getSendQueues();
		for (int i = 0; i < this.speeds.length; i++) {
			this.speeds[i].setOnValueChangedListener(new SpeedBarValueChangedListener(qs[i], i));
		}
		this.connectButton.setOnClickListener(connectClickListener);
		this.exitControl.setOnClickListener(exitControlClickListener);
		this.controlView.setOnTouchListener(speedControlTouchListener);
	}
	
	private void setupDevicesForSpinner() {
		Set<BluetoothDevice> pairedDevices = this.btAdapter.getBondedDevices();
		List<FriendBtDevice> deviceList = new ArrayList<FriendBtDevice>(pairedDevices.size() + 1);
		deviceList.add(new FriendBtDevice(null, " | ", "[Auto search]"));
		for (BluetoothDevice device : pairedDevices) {
			deviceList.add(new FriendBtDevice(device, " | "));
		}
		
		ArrayAdapter<FriendBtDevice> adapter = new ArrayAdapter<FriendBtDevice>(this, android.R.layout.simple_spinner_item, deviceList);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		this.deviceSelection.setAdapter(adapter);
		this.deviceSelection.setTag(deviceList);
		this.loadCamSelection();
	}
	
	private void fillSizesIntoSpinner(CameraCommand cmd) {
		int count = cmd.getFormat();
		byte[] data = cmd.getImageData();
		if (data != null) {
			String[] sizes = new String[count];
			for (int i = 0; i < count; i++) {
				int w = this.readIntFromByteArray(data, i << 3);
				int h = this.readIntFromByteArray(data, (i << 3) + 4);
				sizes[i] = String.format("%d x %d", w, h);
			}
			
			ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, sizes);
			adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			this.previewSizes.setAdapter(adapter);
		}
	}
	
	private int readIntFromByteArray(byte[] buff, int offset) {
		int v = 0;
		for (int i = 0; i < 4; i++) {
			int t = 0xff & buff[offset + i];
			v |= (t << i);
		}
		return v;
	}
	
	private void saveCamSelection(int pos) {
		SharedPreferences.Editor editor = MainActivity.this.getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit();
		Log.d(TAG, "Cam Selected: " + pos);
		editor.putInt(KEY_CAM_SELECTION, pos);
		editor.commit();
	}
	
	private void loadCamSelection() {
		SharedPreferences sp = this.getSharedPreferences(PREF_NAME, MODE_PRIVATE);
		this.deviceSelection.setSelection(sp.getInt(KEY_CAM_SELECTION, 0));
	}
	
	private void btNotSupported() {
		Log.d(TAG, "Bluetooth is not supported on this device");
	}
	
	private void btEnableCancelled() {
		Log.d(TAG, "User cancelled bluetooth enabling");
	}
	
	private Bitmap extractBitmap(byte[] data, int w, int h, int format) {
//		YuvImage yuvImage = new YuvImage(out.toByteArray(), format, w, h, null);
//		ByteArrayOutputStream jpgOut = new ByteArrayOutputStream();
//		Rect rect = new Rect(0, 0, w, h);
//		
//		Bitmap bmp = null;
//		if (yuvImage.compressToJpeg(rect, 100, jpgOut)) {
//			byte[] jpgData = jpgOut.toByteArray();
//			bmp = BitmapFactory.decodeByteArray(jpgData, 0, jpgData.length);
//		}
//		return bmp;
		
		byte[] jpg = uncompressData(data);
		
		return BitmapFactory.decodeByteArray(jpg, 0, jpg.length);
	}
	
	private byte[] uncompressData(byte[] data) {
		return this.uncompressDataGZIP(data);
	}
	
	private byte[] uncompressDataGZIP(byte[] data) {
		ByteArrayInputStream in = new ByteArrayInputStream(data);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			GZIPInputStream gzipIn = new GZIPInputStream(in);
			for (int b = gzipIn.read(); b >= 0; b = gzipIn.read()) {
				out.write(b);
			}
			out.flush();
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return out.toByteArray();
	}
	
	private void changePreviewSize(int index) {
		CameraCommand cmd = new CameraCommand();
		cmd.setCommand(Constants.SIZE);
		cmd.setFormat(index);
		this.camCommunicator.sendCommand(cmd);
	}
	
	private void displayCameraPreview(Bitmap bmp, int w, int h) {
		if (bmp != null) {
			if (this.dirtyRect == null) {
				this.dirtyRect = new Rect();
			}
			boolean sizeChanged = w != this.dirtyRect.width() || h != this.dirtyRect.height();
			if (sizeChanged) {
				int ww = this.cameraSv.getWidth();
				int hh = this.cameraSv.getHeight();
				this.dirtyRect.left = (ww - w) >> 1;
				if (this.dirtyRect.left < 0) {
					this.dirtyRect.left = 0;
				}
				this.dirtyRect.top = (hh - h) >> 1;
				if (this.dirtyRect.top < 0) {
					this.dirtyRect.top = 0;
				}
				this.dirtyRect.right = this.dirtyRect.left + w;
				this.dirtyRect.bottom = this.dirtyRect.top + h;
				Log.d(TAG, String.format("(%d, %d) - (%d, %d), Rect: %s", ww, hh, w, h, this.dirtyRect.toString()));
			}
			Canvas canvas = this.cameraSh.lockCanvas();
			if (canvas != null) {
				canvas.drawColor(this.svBgColor);
				canvas.drawBitmap(bmp, this.dirtyRect.left, this.dirtyRect.top, null);
			}
			this.cameraSh.unlockCanvasAndPost(canvas);
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, "Activity creating...");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		this.initComponents();
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
				if (this.deviceSelection.getTag() != null) {
//					this.connectDevices();
					this.connectRobot();
				} else {
					this.setupDevicesForSpinner();
				}
			} else {
				this.btEnableCancelled();
			}
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onStop() {
		Log.d(TAG, "Activity stopping...");
		super.onStop();
		if (this.setupView.getVisibility() != View.VISIBLE) {
			this.disconnectRobot();
			this.disconnectCamera();
			this.finish();
		}
	}
}
