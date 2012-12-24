package org.programus.nxt.android.lookie_rc.activities;

import java.io.IOException;

import org.programus.lookie.lib.comm.CommandMessage;
import org.programus.lookie.lib.utils.Constants;
import org.programus.lookie.lib.utils.SimpleQueue;
import org.programus.nxt.android.lookie_rc.R;
import org.programus.nxt.android.lookie_rc.comm.DataBuffer;
import org.programus.nxt.android.lookie_rc.comm.NXTData;
import org.programus.nxt.android.lookie_rc.comm.RobotBtCommunicator;
import org.programus.nxt.android.lookie_rc.widgets.SpeedBar;
import org.programus.nxt.android.lookie_rc.widgets.SpeedBar.OnValueChangedListener;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {
	private final static String TAG = "Main";
	
	private Button connectButton;
	private TextView logText;
	
	private SpeedBar[] speeds = new SpeedBar[2];
	private float[] speedTargets = new float[2];
	private DataBuffer dbuff = DataBuffer.getInstance();
	
	private View setupView;
	private View controlView;
	
	private RobotBtCommunicator nxtCommunicator;
	private BluetoothAdapter btAdapter;
	
	private boolean connectedRobot;
	private boolean connectedCamera = true; // TODO test code
	
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
				break;
			}
			case Constants.MSG_WHAT_IOEXCEPTION: {
				IOException e = (IOException) b.getSerializable(Constants.KEY_IOEXCEPTION);
				String text = b.getString(Constants.KEY_MESSAGE);
				p.logText.append(text);
				p.logText.append(Constants.BR);
				p.logText.append(e.getMessage());
				Log.e(TAG, text, e);
				break;
			}
			case Constants.MSG_WHAT_DATA_READ: {
				NXTData data = (NXTData) b.getSerializable(Constants.KEY_NXT_DATA);
				int dir = data.getDir();
				float speed = data.getSpeed();
				Log.d(TAG, String.format("Actual: dir: %d, v: %f", dir, speed));
				p.speeds[dir].setActualValue(speed);
				p.speeds[dir].invalidate();
				break;
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
				connectDevices();
//				robotConnected(true);
			}
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
	
	private void connectDevices() {
		connectRobot();
	}
	
	private void connectRobot() {
		if (nxtCommunicator == null) {
			nxtCommunicator = new RobotBtCommunicator(handler);
		}
		logText.append("Connecting to NXT...");
		logText.append(Constants.BR);
		nxtCommunicator.connect();
	}
	
	private void devicesConnected() {
		if (this.connectedRobot && this.connectedCamera) {
			this.setupView.setVisibility(View.GONE);
			this.controlView.setVisibility(View.VISIBLE);
			this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		} else {
			this.setupView.setVisibility(View.VISIBLE);
			this.controlView.setVisibility(View.GONE);
			this.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
	}
	
	private void robotConnected(boolean isConnected) {
		this.connectedRobot = isConnected;
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
		this.logText = (TextView) this.findViewById(R.id.logText);
		
		this.speeds[Constants.LEFT] = (SpeedBar) this.findViewById(R.id.leftSpeedBar);
		this.speeds[Constants.RIGHT] = (SpeedBar) this.findViewById(R.id.rightSpeedBar);
		this.setupView = this.findViewById(R.id.setupView);
		this.controlView = this.findViewById(R.id.controlView);
		
		this.devicesConnected();
		
		SimpleQueue<CommandMessage>[] qs = dbuff.getSendQueues();
		for (int i = 0; i < this.speeds.length; i++) {
			this.speeds[i].setOnValueChangedListener(new SpeedBarValueChangedListener(qs[i], i));
		}
		this.connectButton.setOnClickListener(connectClickListener);
		this.controlView.setOnTouchListener(speedControlTouchListener);
	}
	
	private void btNotSupported() {
		Log.d(TAG, "Bluetooth is not supported on this device");
	}
	
	private void btEnableCancelled() {
		Log.d(TAG, "User cancelled bluetooth enabling");
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
				this.connectDevices();
			} else {
				this.btEnableCancelled();
			}
		}
	}

	@Override
	protected void onDestroy() {
		Log.d(TAG, "Activity destroying...");
		if (this.nxtCommunicator != null) {
			try {
				this.nxtCommunicator.end();
			} catch (IOException e) {
				Log.e(TAG, "Close connection with robot error.", e);
			}
		}
		super.onDestroy();
	}
}
