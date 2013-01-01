package org.programus.nxt.android.lookie_camera.activites;

import org.programus.nxt.android.lookie_camera.R;
import org.programus.nxt.android.lookie_camera.video.VideoInformation;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class VideoSelectActivity extends Activity {
	private static class VideoInforAdapter extends ArrayAdapter<VideoInformation> {
		private int layout = R.layout.video_list_item_row;
		private Context context;
		private VideoInformation[] videos;
		public VideoInforAdapter(Context context, VideoInformation[] videos) {
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
				VideoInformation vi = this.videos[position];
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
	
	private ListView videoList;
	private VideoInforAdapter adapter;
	private VideoInformation[] videos;
	
	private AdapterView.OnItemClickListener videoSelectListener = new AdapterView.OnItemClickListener() {
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			VideoInformation video = videos[position];
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
	
	private void initComponents() {
		this.videos = VideoInformation.getVideoList();
		this.videoList = (ListView) this.findViewById(R.id.videoList);
		this.adapter = new VideoInforAdapter(this, this.videos);
		
		this.videoList.setAdapter(adapter);
		this.videoList.setOnItemClickListener(videoSelectListener);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_video_select);
		
		this.initComponents();
	}
}
