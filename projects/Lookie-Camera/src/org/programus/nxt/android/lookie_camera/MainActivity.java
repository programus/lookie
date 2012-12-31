package org.programus.nxt.android.lookie_camera;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

import org.programus.lookie.lib.comm.CameraCommand;
import org.programus.lookie.lib.utils.Constants;
import org.programus.lookie.lib.utils.MathUtil;
import org.programus.lookie.lib.utils.SimpleQueue;
import org.programus.nxt.android.lookie_camera.comm.CommandReceiver;
import org.programus.nxt.android.lookie_camera.comm.DataBuffer;
import org.programus.nxt.android.lookie_camera.services.MainService;
import org.programus.nxt.android.lookie_camera.utils.Logger;
import org.programus.nxt.android.lookie_camera.video.ImageTransporter;
import org.programus.nxt.android.lookie_camera.video.JpegVideoRecorder;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageButton;
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
	private ImageButton recordButton;
	private ImageButton camButton;
	
	private SurfaceView sv;
	private SurfaceHolder sHolder;
	private Camera camera;
	private ImageTransporter imgTransporter;
	
//	private MediaRecorder recorder;
	private JpegVideoRecorder vrecorder;
//	private boolean recording;
	@SuppressLint("SimpleDateFormat")
	private SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
//	private CamcorderProfile profile;
	private Point origSize = new Point();
	
	private int previewFormat = ImageFormat.NV21;
	
	private BluetoothAdapter btAdapter;
	private Logger logger = Logger.getInstance();
	
	private Point sendSize = new Point();
	private int sendQuality = Constants.COMPRESS_RATE;
	
	private SensorManager sensorMgr;
	private Sensor sensor;
	
	private CommandReceiver receiver;
	
	private Queue<CameraCommand> sendQ = DataBuffer.getInstance().getSendQueue();
	private SimpleQueue<CameraCommand> imageQ = DataBuffer.getInstance().getImageQueue();
	
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
					p.setSendSize(cmd.getWidth(), cmd.getHeight());
					break;
				case Constants.QUALITY:
					p.setSendQuality(cmd.getFormat());
					break;
				case Constants.LIGHT:
					if (cmd.getFormat() > 0) {
						p.turnFlashLightOn();
					} else {
						p.turnFlashLightOff();
					}
					break;
				case Constants.RECORD:
					if (cmd.getFormat() > 0) {
//						p.startRecording();
						p.startVideoRecording();
						p.feedbackRecordResult();
					} else {
//						p.stopRecording();
						p.stopVideoRecording();
					}
					break;
				case Constants.FOCUS:
					p.autoFocus();
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
	
	private View.OnClickListener recordListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			if (vrecorder.isRecording()) {
//				stopRecording();
				stopVideoRecording();
				recordButton.setImageResource(android.R.drawable.presence_video_online);
			} else if (cameraStarted){
//				startRecording();
				startVideoRecording();
				if (vrecorder.isRecording()) {
					recordButton.setImageResource(android.R.drawable.presence_video_busy);
				}
			}
		}
	};
	
	private View.OnClickListener camListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			if (cameraStarted) {
				stopCameraPreview();
				camButton.setImageResource(android.R.drawable.presence_online);
			} else {
				cameraStarted = true;
				startCameraPreview();
				camButton.setImageResource(android.R.drawable.presence_audio_busy);
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
		@Override
		public void onPreviewFrame(byte[] data, Camera camera) {
			long time = System.currentTimeMillis();
			Log.d(TAG, "preview frame:" + time);
			imgTransporter.transportFrame(data, origSize.x, origSize.y, sendSize.x, sendSize.y, time, previewFormat, sendQuality);
			if (vrecorder.isRecording()) {
				vrecorder.putFrame(data, time);
			}
		}
	};
	
	private Camera.AutoFocusCallback focusCallback = new Camera.AutoFocusCallback() {
		@Override
		public void onAutoFocus(boolean success, Camera camera) {
			Log.d(TAG, "focused.");
			CameraCommand cmd = new CameraCommand();
			cmd.setCommand(Constants.FOCUS);
			cmd.setFormat(success ? 1 : 0);
			sendQ.offer(cmd);
		}
	};
	
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
				this.sendMaxSize(this.origSize.x, this.origSize.y);
			}
			this.imgTransporter.start();
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
		params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
		params.setPreviewFormat(this.previewFormat);
		
		int maxWidth = 0;
		for (Camera.Size size : params.getSupportedPreviewSizes()) {
			if (size.width > maxWidth && size.width < Constants.SIZE_MAX_WIDTH) {
				maxWidth = size.width;
				this.origSize.x = Math.max(size.width, size.height);
				this.origSize.y = Math.min(size.width, size.height);
			}
		}
		params.setPreviewSize(this.origSize.x, this.origSize.y);
		
//		this.profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
		this.setupDefaultSendSize(this.origSize.x, this.origSize.y);
	}
	
//	/**
//	 * This method is not be used since retrieving preview data is not possible while recording on android.
//	 */
//	private boolean prepareVideoRecorder() {
//		boolean success = true;
//		// Pre-step. check SD-card and file
//		File outputFile = this.getOutputVideoFile();
//		if (outputFile == null) {
//			success = false;
//		} else {
//			// Step 0.1 initialize recorder
//			this.recorder = new MediaRecorder();
//			
//			// Step 1. unlock and set camera to recorder
//			this.camera.unlock();
//			this.recorder.setCamera(camera);
//			
//			// Step 2. Set sources
//			this.recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
//			this.recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
//			
//			// Step 3. Set a CamcorderProfile
//			this.recorder.setProfile(this.profile);
////			this.recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
////			this.recorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
////			this.recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
////			this.recorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);
////			this.recorder.setVideoFrameRate(5);
////			this.recorder.setVideoEncodingBitRate(8);
//			
//			// Step 4. Set output file
//			this.recorder.setOutputFile(outputFile.getAbsolutePath());
//			
//			// Step 5. Set the preview output
//			this.recorder.setPreviewDisplay(this.sHolder.getSurface());
//			
//			// Step 6. Prepare configured MediaRecorder
//			try {
//				this.recorder.prepare();
//				success = true;
//			} catch (IllegalStateException e) {
//				logger.log("IllegalStateException when prepare recorder: " + e.getMessage());
//				this.releaseVideoRecorder();
//				success = false;
//			} catch (IOException e) {
//				logger.log("IOException when prepare recorder: " + e.getMessage());
//				this.releaseVideoRecorder();
//				success = false;
//			}
//		}
//		
//		return success;
//	}
//	
//	/**
//	 * This method is not be used since retrieving preview data is not possible while recording on android.
//	 */
//	private void releaseVideoRecorder() {
//		if (this.recorder != null) {
//			this.recorder.reset();
//			this.recorder.release();
//			this.recorder = null;
//			try {
//				this.camera.reconnect();
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
//			this.camera.stopPreview();
//			Camera.Parameters params = this.camera.getParameters();
//			params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
//			this.camera.setParameters(params);
//			this.camera.setPreviewCallback(camPreviewCallback);
//			this.camera.startPreview();
//		}
//	}
	
	private void startVideoRecording() {
		this.vrecorder.setOutputFilePath(getOutputImagePath());
		this.vrecorder.setVideoQuality(Constants.VIDEO_QUALITY);
		this.vrecorder.setVideoSize(this.origSize.x, this.origSize.y);
		this.vrecorder.setFrameFormat(previewFormat);
		this.vrecorder.setFPS(Constants.MAX_FPS);
		this.vrecorder.startRecord();
	}
	
//	/**
//	 * This method is not be used since retrieving preview data is not possible while recording on android.
//	 */
//	private void startRecording() {
//		// Setup camera parameters for video recording
//		this.camera.stopPreview();
//		Camera.Parameters params = this.camera.getParameters();
//		params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
//		this.camera.setParameters(params);
//		this.camera.startPreview();
//		
//		try {
//			if (this.prepareVideoRecorder()) {
//				Log.d(TAG, "Start record...");
//				this.recorder.start();
//				Log.d(TAG, "record started");
//				try {
//					this.camera.reconnect();
//				} catch (Exception e) {
//					e.printStackTrace();
//				}
//				this.recording = true;
//			} else {
//				this.recording = false;
//				this.releaseVideoRecorder();
//			}
//		} catch (Exception e) {
//			this.recording = false;
//			e.printStackTrace();
//			this.releaseVideoRecorder();
//		}
//	}
	
	private void feedbackRecordResult() {
		CameraCommand cmd = new CameraCommand();
		cmd.setCommand(Constants.RECORD);
		cmd.setFormat(this.vrecorder.isRecording() ? 1 : 0);
		Log.d(TAG, "Feedback: " + this.vrecorder.isRecording());
		this.sendQ.offer(cmd);
	}
	
	private void stopVideoRecording() {
		if (this.vrecorder.isRecording()) {
			this.vrecorder.stopRecord();
		}
	}
	
//	/**
//	 * This method is not be used since retrieving preview data is not possible while recording on android.
//	 */
//	private void stopRecording() {
//		if (this.recording) {
//			this.recorder.stop();
//			this.releaseVideoRecorder();
//			this.recording = false;
//			
//			this.resetCameraFocusMode();
//		}
//	}
//	
//	private void resetCameraFocusMode() {
//		this.camera.stopPreview();
//		Camera.Parameters params = this.camera.getParameters();
//		params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
//		this.camera.setParameters(params);
//		this.camera.startPreview();
//	}
	
	private void autoFocus() {
		if (this.cameraStarted) {
			Log.d(TAG, "auto focus");
			this.camera.autoFocus(focusCallback);
		}
	}
	
//	/**
//	 * This method is not be used since retrieving preview data is not possible while recording on android.
//	 */
//	private File getOutputVideoFile() {
//		File file = null;
//		if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
//			File path = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Lookie_Record");
//			if (!path.exists()) {
//				if (!path.mkdirs()) {
//					logger.log("Video save directory cannot be created:" + path.getAbsolutePath());
//					return null;
//				}
//			}
//			
//			String timestamp = this.sdf.format(Calendar.getInstance().getTime());
//			String filename = String.format("LookieVID_%s.mp4", timestamp);
//			file = new File(path, filename);
//		}
//		return file;
//	}
	
	private File getOutputImagePath() {
		File file = null;
		if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
			File path = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Lookie_Record");
			if (!path.exists()) {
				if (!path.mkdirs()) {
					logger.log("Video save directory cannot be created:" + path.getAbsolutePath());
					return null;
				}
			}
			
			String timestamp = this.sdf.format(Calendar.getInstance().getTime());
			String filename = String.format(Locale.ENGLISH, "Lookie_JPGs_%s", timestamp);
			file = new File(path, filename);
			if (!file.exists()) {
				if (!file.mkdirs()) {
					file = null;
				}
			}
		}
		return file;
	}
	
	private void setupDefaultSendSize(int width, int height) {
		int w = Constants.SIZE_DEFAULT_WIDTH;
		int h = w * height / width;
		h = (h >>> 1) << 1;
		this.sendSize.set(w, h);
	}
	
	private void sendMaxSize(int width, int height) {
		CameraCommand cmd = new CameraCommand();
		cmd.setCommand(Constants.SIZE);
		cmd.setWidth(width);
		cmd.setHeight(height);
		this.sendQ.offer(cmd);
	}
	
	private void setSendSize(int width, int height) {
		this.sendSize.set(width, height);
	}
	
	private void setSendQuality(int quality) {
		this.sendQuality = quality;
	}
	
	private void turnFlashLightOn() {
		if (this.camera != null) {
			logger.log("Trying to turn on light");
			Camera.Parameters params = this.camera.getParameters();
			List<String> modes = params.getSupportedFlashModes();
			logger.log("Supported flash mode: " + modes);
			String currMode = params.getFlashMode();
			logger.log("current flash mode: " + currMode);
			if (!Camera.Parameters.FLASH_MODE_TORCH.equals(currMode) && modes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
				logger.log("turning on light");
				this.camera.stopPreview();
				params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
				this.camera.setParameters(params);
				this.camera.startPreview();
				logger.log("light turned on");
			}
		}
	}
	
	private void turnFlashLightOff() {
		if (this.camera != null) {
			logger.log("Trying to turn off light");
			Camera.Parameters params = this.camera.getParameters();
			List<String> modes = params.getSupportedFlashModes();
			String currMode = params.getFlashMode();
			if (!Camera.Parameters.FLASH_MODE_OFF.equals(currMode) && modes.contains(Camera.Parameters.FLASH_MODE_OFF)) {
				this.camera.stopPreview();
				params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
				this.camera.setParameters(params);
				this.camera.startPreview();
			}
		}
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
			this.imgTransporter.stop();
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
		this.recordButton = (ImageButton) this.findViewById(R.id.recordButton);
		this.recordButton.setOnClickListener(recordListener);
		this.camButton = (ImageButton) this.findViewById(R.id.camButton);
		this.camButton.setOnClickListener(camListener);
		
		this.imgTransporter = new ImageTransporter(this.imageQ);
		this.vrecorder = new JpegVideoRecorder();
		
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
