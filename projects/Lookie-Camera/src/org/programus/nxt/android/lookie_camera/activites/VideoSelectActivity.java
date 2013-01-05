package org.programus.nxt.android.lookie_camera.activites;

import java.io.File;
import java.util.List;

import org.programus.nxt.android.lookie_camera.R;
import org.programus.nxt.android.lookie_camera.video.VideoInformation;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class VideoSelectActivity extends Activity {
	private final static String TAG = "VIDSEL";
	private static class VideoInforAdapter extends ArrayAdapter<VideoInformation> {
		private int layout = R.layout.video_list_item_row;
		private Context context;
		private List<VideoInformation> videos;
		public VideoInforAdapter(Context context, List<VideoInformation> videos) {
			super(context, R.layout.video_list_item_row, videos);
			this.context = context;
			this.videos = videos;
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View row = convertView;
			ViewHolder holder = null;
			
			if (row == null) {
				LayoutInflater inflater = ((Activity) context).getLayoutInflater();
				row = inflater.inflate(layout, parent, false);
				holder = new ViewHolder();
				holder.previewImage = (ImageView) row.findViewById(R.id.previewImage);
				holder.name = (TextView) row.findViewById(R.id.videoName);
				holder.information = (TextView) row.findViewById(R.id.videoInfor);
				
				row.setTag(holder);
			} else {
				holder = (ViewHolder) row.getTag();
			}
			
			if (holder != null) {
				VideoInformation vi = this.videos.get(position);
				holder.previewImage.setImageBitmap(vi.getPreviewImage());
				holder.name.setText(vi.getName());
				holder.information.setText(String.format("%d x %d \nfps:%.2f quality: %d frames:%d", vi.getWidth(), vi.getHeight(), vi.getFps(), vi.getQuality(), vi.getFrames()));
			}
			
			return row;
		}
		
		private static class ViewHolder {
			private ImageView previewImage;
			private TextView name;
			private TextView information;
		}
	}
	
	private static class UIHandler extends Handler {
		private VideoSelectActivity p;
		public UIHandler(VideoSelectActivity parent) {
			this.p = parent;
		}
		@Override
		public void handleMessage(Message msg) {
			Bundle b = msg.getData();
			switch (msg.what) {
			case MSG_WHAT_DELETE_PROGRESS_INCREASED:
				p.onDeleteProgressIncreased();
				break;
			case MSG_WHAT_DELETE_RESULT:
				p.onDeleteCompleted(b.getInt(KEY_INDEX), b.getBoolean(KEY_RESULT));
				break;
			default:
				super.handleMessage(msg);
				break;
			}
		}
	}
	
	private final static int MSG_WHAT_DELETE_PROGRESS_INCREASED = 1;
	private final static int MSG_WHAT_DELETE_RESULT = 2;
	private final static String KEY_RESULT = "result";
	private final static String KEY_INDEX = "index";
	
	private Handler handler = new UIHandler(this);
	private ProgressDialog progressDialog;
	private ListView videoList;
	private VideoInforAdapter adapter;
	private List<VideoInformation> videos;
	
	private AdapterView.OnItemClickListener videoSelectListener = new AdapterView.OnItemClickListener() {
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			VideoInformation video = videos.get(position);
			Intent intent = new Intent(VideoSelectActivity.this, VideoPlayActivity.class);
			intent.putExtra(VideoInformation.class.getName(), video);
			clearVideoPlayerState();
			VideoSelectActivity.this.startActivity(intent);
		}
	};
	
	private void clearVideoPlayerState() {
		SharedPreferences.Editor editor = this.getSharedPreferences(VideoPlayActivity.SP_STORE_KEY, MODE_PRIVATE).edit();
		editor.clear();
		editor.commit();
	}
	
	private void initProgressDialog(int max) {
		this.progressDialog = new ProgressDialog(this);
		this.progressDialog.setMax(max);
		this.progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		this.progressDialog.setTitle("Delete");
		this.progressDialog.setMessage("Deleting Video");
		this.progressDialog.setIndeterminate(false);
		this.progressDialog.setCancelable(false);
	}
	
	private void deleteVideo(final int index) {
		final VideoInformation vi = this.videos.get(index);
		final File path = vi.getPath();
		final File[] files = path.listFiles();
		this.initProgressDialog(files.length);
		this.progressDialog.show();
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				boolean success = true;
				for (File f : files) {
					success = success && f.delete();
					if (!success) {
						break;
					} else {
						this.increaseProgress();
					}
					Thread.yield();
				}
				if (success) {
					success = path.delete();
					this.increaseProgress();
				}
				this.sendResult(success);
			}
			
			private void increaseProgress() {
				handler.sendEmptyMessage(MSG_WHAT_DELETE_PROGRESS_INCREASED);
			}
			
			private void sendResult(boolean success) {
				Bundle b = new Bundle();
				b.putInt(KEY_INDEX, index);
				b.putBoolean(KEY_RESULT, success);
				Message msg = new Message();
				msg.what = MSG_WHAT_DELETE_RESULT;
				msg.setData(b);
				handler.sendMessage(msg);
			}
		});
		t.start();
	}
	
	private void onDeleteProgressIncreased() {
		if (this.progressDialog != null) {
			this.progressDialog.incrementProgressBy(1);
			Log.d(TAG, String.format("delete progress: %d/%d", this.progressDialog.getProgress(), this.progressDialog.getMax()));
		}
	}
	
	private void onDeleteCompleted(int index, boolean success) {
		if (success) {
			this.videos.remove(index);
			this.adapter.notifyDataSetChanged();
		} else {
			Toast.makeText(this, "Delete Video Failed.", Toast.LENGTH_LONG).show();
		}
		this.progressDialog.dismiss();
		this.progressDialog = null;
	}
	
	private void initComponents() {
		this.videos = VideoInformation.getVideoList();
		this.videoList = (ListView) this.findViewById(R.id.videoList);
		this.adapter = new VideoInforAdapter(this, this.videos);
		
		this.videoList.setAdapter(adapter);
		this.videoList.setOnItemClickListener(videoSelectListener);
		
		this.registerForContextMenu(this.videoList);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_video_select);
		
		this.initComponents();
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		boolean ret = true;
		switch (item.getItemId()) {
		case R.id.menu_delete:
			this.deleteVideo(info.position);
			break;
		default:
			ret = super.onContextItemSelected(item);
		}
		return ret;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = this.getMenuInflater();
		inflater.inflate(R.menu.video_select_context, menu);
	}
}
