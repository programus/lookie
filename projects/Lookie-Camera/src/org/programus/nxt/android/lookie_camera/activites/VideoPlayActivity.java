package org.programus.nxt.android.lookie_camera.activites;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import org.programus.lookie.lib.utils.FixedLengthQueue;
import org.programus.lookie.lib.utils.SimpleQueue;
import org.programus.nxt.android.lookie_camera.R;
import org.programus.nxt.android.lookie_camera.video.VideoInformation;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class VideoPlayActivity extends Activity {
	private final static String TAG = "VideoPlayer";
	
	private final static int MSG_WHAT_PLAY_FRAME = 1;
	private final static int MSG_WHAT_STOP = 2;
	
	private final static String KEY_PLAY_PROGRESS = "load.progress";
	
	public final static String SP_STORE_KEY = "VideoPlay.SP";
	
	private final static String SP_KEY_INDEX = "save.index";
	private final static String SP_KEY_FILE = "save.file";
	
	private SurfaceView sv;
	private SurfaceHolder sh;
	private SeekBar frameBar;
	private TextView loadingText;
	private TextView frameText;
	private TextView nameTitle;
	
	private View ctrlView;
	private View touchPlayView;
	
	private MediaPlayer audioPlayer;
	
	private boolean playing;
	
	private VideoInformation video;
	
	private final static int BUFF_LIMIT = 3;
	private SimpleQueue<Bitmap> bmpQ = new FixedLengthQueue<Bitmap>(BUFF_LIMIT);
	
	private Timer playTimer;
	private volatile int index;
	
	private BitmapFactory.Options bmpOptions;
	
	private Handler handler = new UIHandler(this);
	
	private static class UIHandler extends Handler {
		private VideoPlayActivity p;
		public UIHandler(VideoPlayActivity parent) {
			this.p = parent;
		}
		@Override
		public void handleMessage(Message msg) {
			Bundle b = msg.getData();
			switch (msg.what) {
			case MSG_WHAT_PLAY_FRAME: {
				p.frameBar.setProgress(b.getInt(KEY_PLAY_PROGRESS));
				break;
			}
			case MSG_WHAT_STOP:
				p.stop();
				break;
			}
		}
	};
	
	private class PlayTask extends TimerTask {
		@Override
		public void run() {
			if (!bmpQ.isEmpty()) {
				Bitmap bmp = null;
				synchronized (bmpQ) {
					bmp = bmpQ.poll();
				}
				if (bmp != null) {
					Log.d(TAG, "draw surface");
					drawSurface(bmp);
					bmp.recycle();
				}
			}
		}
	};
	
	private class ImageLoadTask extends TimerTask {
		@Override
		public void run() {
			if (playing && bmpQ.size() < BUFF_LIMIT) {
				if (index < video.getImages().length) {
					showFrame(index++);
				} else if (bmpQ.isEmpty()) {
					// loaded to the last image and all images are drawn.
					Log.d(TAG, "no frame to play");
					handler.sendEmptyMessage(MSG_WHAT_STOP);
				}
			}
		}
	};
	
	private View.OnClickListener playStopListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			if (playing) {
				Log.d(TAG, "click to stop");
				stop();
			} else {
				Log.d(TAG, "click to start");
				play();
			}
		}
	};
	
	private SeekBar.OnSeekBarChangeListener playFrameListener = new SeekBar.OnSeekBarChangeListener() {
		private int value;
		
		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
			index = this.value;
			loadImage(index);
		}
		
		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {
		}
		
		@Override
		public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			this.value = progress;
			if (this.value >= seekBar.getMax()) {
				this.value = seekBar.getMax() - 1;
			}
			setProgressText(this.value);
		}
	};
	
	private void setProgressText(int progress) {
		frameText.setText(String.format("frame: %d", progress));
	}
	
	private void showFrame(int index) {
		this.loadImage(index);
	}
	
	private void initComponents() {
		this.sv = (SurfaceView) this.findViewById(R.id.playSurface);
		this.sh = sv.getHolder();
		this.frameBar = (SeekBar) this.findViewById(R.id.frameBar);
		this.loadingText = (TextView) this.findViewById(R.id.loadingText);
		this.frameText = (TextView) this.findViewById(R.id.frameText);
		this.nameTitle = (TextView) this.findViewById(R.id.nameTitle);
		this.ctrlView = this.findViewById(R.id.ctrlView);
		this.touchPlayView = this.findViewById(R.id.touchPlayView);
		
		this.touchPlayView.setOnClickListener(playStopListener);
		this.frameBar.setOnSeekBarChangeListener(playFrameListener);
		
		this.ctrlView.setVisibility(View.GONE);
		this.loadingText.setVisibility(View.GONE);
		
		this.bmpOptions = new BitmapFactory.Options();
		bmpOptions.inPurgeable = true;
		bmpOptions.inDither = false;
		bmpOptions.inInputShareable = true;
	}
	
	private void initPlayer() {
		TimerTask imageLoadTask = new ImageLoadTask();
		TimerTask playTask = new PlayTask();
		this.playTimer = new Timer("Video player", true);
		this.playTimer.schedule(imageLoadTask, 0, 1);
		this.playTimer.scheduleAtFixedRate(playTask, 0, (long) (1000 / video.getFps()));
	}
	
	private void getDataFromIntent() {
		Intent intent = this.getIntent();
		Bundle b = intent.getExtras();
		this.video = (VideoInformation) b.get(VideoInformation.class.getName());
		
		this.frameBar.setMax(this.video.getFrames());
		this.nameTitle.setText(this.video.getName());
	}
	
	private void loadImage(int index) {
		Log.d(TAG, "load image:" + index);
		if (index < video.getImages().length && index >= 0) {
			File img = video.getImages()[index];
			Bitmap bmp = BitmapFactory.decodeFile(img.getAbsolutePath(), this.bmpOptions);
			if (bmp != null) {
				synchronized (bmpQ) {
					bmpQ.offer(bmp);
				}
			}
		}
	}
	
	private void updateProgress() {
		int progress = 0;
		synchronized (bmpQ) {
			progress = this.index + bmpQ.size();
		}
		
		Message msg = new Message();
		Bundle b = new Bundle();
		b.putInt(KEY_PLAY_PROGRESS, progress);
		msg.setData(b);
		msg.what = MSG_WHAT_PLAY_FRAME;
		this.handler.sendMessage(msg);
	}
	
	private void play() {
		if (this.index >= this.video.getFrames() - 1) {
			Log.d(TAG, "reset index:" + index);
			this.index = 0;
		}
		this.playing = true;
		this.playAudio();
		this.ctrlView.setVisibility(View.GONE);
		this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}
	
	private void playAudio() {
		if (this.prepareAudio()) {
			int position = (int) (1000 * index / video.getFps());
			this.audioPlayer.seekTo(position);
			this.audioPlayer.start();
		}
	}
	
	private boolean prepareAudio() {
		boolean success = true;
		File audioFile = this.video.getAudioFile();
		success = audioFile.exists();
		if (success) {
			this.audioPlayer = new MediaPlayer();
			try {
				this.audioPlayer.setDataSource(audioFile.getAbsolutePath());
				this.audioPlayer.prepare();
			} catch (Exception e) {
				this.audioPlayer.release();
				this.audioPlayer = null;
				success = false;
				e.printStackTrace();
			}
		}
		return success;
	}
	
	private void stop() {
		this.playing = false;
		this.stopAudio();
		synchronized (bmpQ) {
			this.index -= bmpQ.size();
			while (!bmpQ.isEmpty()) {
				bmpQ.poll().recycle();
			}
		}
		this.updateProgress();
		this.ctrlView.setVisibility(View.VISIBLE);
		this.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}
	
	private void stopAudio() {
		if (this.audioPlayer != null) {
			this.audioPlayer.pause();
			this.audioPlayer.release();
			this.audioPlayer = null;
		}
	}
	
	private void drawSurface(Bitmap bmp) {
		if (bmp != null) {
			Rect srcRect = new Rect(0, 0, bmp.getWidth(), bmp.getHeight());
			Rect dstRect = new Rect(0, 0, sv.getWidth(), sv.getHeight());
			int ew = bmp.getWidth() * sv.getHeight() / bmp.getHeight();
			if (ew < bmp.getWidth()) {
				dstRect.right = ew;
				dstRect.bottom = bmp.getHeight() * ew / bmp.getWidth();
			}
			int dx = (sv.getWidth() - dstRect.right) >> 1;
			int dy = (sv.getHeight() - dstRect.bottom) >> 1;
			dstRect.offset(dx, dy);
			
			synchronized (sh) {
				Log.d(TAG, "Start drawing bmp");
				Canvas canvas = sh.lockCanvas();
				if (canvas != null) {
					canvas.drawColor(Color.BLACK);
					canvas.drawBitmap(bmp, srcRect, dstRect, null);
					sh.unlockCanvasAndPost(canvas);
				}
				Log.d(TAG, "Stop bmp");
			}
		}
	}
	
	private void saveState() {
		SharedPreferences.Editor editor = this.getSharedPreferences(SP_STORE_KEY, MODE_PRIVATE).edit();
		editor.putInt(SP_KEY_INDEX, index);
		editor.putString(SP_KEY_FILE, this.video.getPath().getAbsolutePath());
		editor.commit();
	}
	
	private void loadState() {
		SharedPreferences sp = this.getSharedPreferences(SP_STORE_KEY, MODE_PRIVATE);
		
		this.index = sp.getInt(SP_KEY_INDEX, 0);
		Log.d(TAG, "loaded index:" + index);
		
		if (this.video == null) {
			String pathname = sp.getString(SP_KEY_FILE, "");
			try {
				this.video = new VideoInformation(pathname);
			} catch (IOException e) {
				e.printStackTrace();
				Toast.makeText(this, "File lost:" + pathname, Toast.LENGTH_LONG).show();
			}
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(R.layout.activity_video_play);
		this.initComponents();
		Log.d(TAG, "created");
	}

	@Override
	protected void onPause() {
		super.onPause();
		this.stop();
		this.playTimer.cancel();
		this.saveState();
		Intent intent = this.getIntent();
		intent.putExtra("index", index);
		this.setIntent(intent);
		Log.d(TAG, "paused");
	}

	@Override
	protected void onResume() {
		super.onResume();
		this.loadState();
		this.stop();
		this.initPlayer();
		this.setProgressText(index);
		this.showFrame(index);
		Log.d(TAG, "resumed");
	}

	@Override
	protected void onStart() {
		super.onStart();
		this.getDataFromIntent();
		Log.d(TAG, "started");
	}

	@Override
	protected void onStop() {
		super.onStop();
		Log.d(TAG, "stopped");
	}

	@Override
	protected void onRestart() {
		super.onRestart();
		Log.d(TAG, "restarted");
	}

}
