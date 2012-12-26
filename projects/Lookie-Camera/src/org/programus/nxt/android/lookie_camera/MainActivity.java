package org.programus.nxt.android.lookie_camera;

import org.programus.lookie.lib.utils.Constants;
import org.programus.nxt.android.lookie_camera.services.MainService;
import org.programus.nxt.android.lookie_camera.utils.Logger;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.ToggleButton;

public class MainActivity extends Activity {
	private final static String PREFS_NAME = "Lookie-Eye.Main";
	private final static String KEY_SVC_STATE = "Service.State";
	private final static String KEY_LOG_TEXT = "Log.Text";
	private ToggleButton toggleServiceButton;
	private TextView logText;
	private BluetoothAdapter btAdapter;
	private Logger logger = Logger.getInstance();
	
	private boolean serviceStarted;
	
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
				endShow();
			}
		}
	};
	
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

	private void initCompoments() {
		this.toggleServiceButton = (ToggleButton) this.findViewById(R.id.toggleServiceButton);
		this.logText = (TextView) this.findViewById(R.id.logText);
		
		this.toggleServiceButton.setOnCheckedChangeListener(toggleServiceChangeListener);
		this.logger.setHandler(this.handler);
		
		SharedPreferences.Editor editor = this.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
		editor.putString(KEY_LOG_TEXT, "");
		editor.commit();
	}
	
	private void beginShow() {
		Intent intent = new Intent(this.getApplicationContext(), MainService.class);
		intent.putExtra(MainService.START_FLAG_KEY, MainService.START_FLAG_START);
		this.startService(intent);
		this.serviceStarted = true;
		logger.log("Service started.");
	}
	
	private void endShow() { 
		Intent intent = new Intent(this.getApplicationContext(), MainService.class);
		this.stopService(intent);
		this.serviceStarted = false;
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
		this.logText.setText(sp.getString(KEY_LOG_TEXT, ""));
		this.toggleServiceButton.setChecked(this.serviceStarted);
	}

	@Override
	protected void onPause() {
		super.onPause();
		SharedPreferences.Editor editor = this.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
		editor.putBoolean(KEY_SVC_STATE, serviceStarted);
		editor.putString(KEY_LOG_TEXT, this.logText.getText().toString());
		editor.commit();
	}

}
