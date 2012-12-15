package org.programus.nxt.android.lookie_rc.activities;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.programus.nxt.android.lookie_rc.R;
import org.programus.nxt.android.lookie_rc.parts.FriendBtDevice;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;

public class BtConnActivity extends Activity {
	
	private final static int REQUEST_ENABLE_BT = 5;
	public final static int RESULT_BT_NOT_SUPPORTED = RESULT_FIRST_USER;
	
	private BluetoothAdapter btAdapter;
	private List<FriendBtDevice> pairedDevs = new ArrayList<FriendBtDevice>();
	private ListView deviceList;
	
	private OnItemClickListener deviceSelectListener = new OnItemClickListener() {
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			
		}
	};
	
	private List<FriendBtDevice> getPairedDevices(BluetoothAdapter adapter, List<FriendBtDevice> list) {
		Set<BluetoothDevice> devSet = adapter.getBondedDevices();
		if (list == null) {
			list = new ArrayList<FriendBtDevice>(devSet.size());
		}
		for (BluetoothDevice dev : devSet) {
			list.add(new FriendBtDevice(dev));
		}
		((BaseAdapter) this.deviceList.getAdapter()).notifyDataSetChanged();
		
		return list;
	}
	
	private boolean turnOnBt() {
		boolean ret = false;
		if (this.btAdapter == null) {
			this.btAdapter = BluetoothAdapter.getDefaultAdapter();
		}
		
		if (this.btAdapter == null) {
			this.setResult(RESULT_BT_NOT_SUPPORTED);
			this.finish();
		} else {
			ret = this.btAdapter.isEnabled();
			if (!ret) {
				Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				this.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
			}
		}
		return ret;
	}
	
	private void initComponents() {
		deviceList = (ListView) this.findViewById(R.id.deviceListView);
		deviceList.setAdapter(new ArrayAdapter<FriendBtDevice>(this, android.R.layout.simple_list_item_1, this.pairedDevs));
		deviceList.setOnItemClickListener(deviceSelectListener);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_bt_conn);
		
		this.initComponents();
		if (this.turnOnBt()) {
			this.getPairedDevices(btAdapter, pairedDevs);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_bt_conn, menu);
		return true;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_ENABLE_BT) {
			if (resultCode == RESULT_OK && this.btAdapter.isEnabled()) {
				this.getPairedDevices(btAdapter, pairedDevs);
			} else {
				this.setResult(RESULT_CANCELED);
				this.finish();
			}
		}
	}

}
