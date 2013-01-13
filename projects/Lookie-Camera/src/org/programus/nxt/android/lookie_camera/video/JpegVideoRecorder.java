package org.programus.nxt.android.lookie_camera.video;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.Queue;

import org.programus.lookie.lib.utils.Constants;
import org.programus.nxt.android.lookie_camera.services.IFrameTransporter;
import org.programus.nxt.android.lookie_camera.services.ImageSaveService;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

public class JpegVideoRecorder {
	public final static int INFO_WHAT_RECORD_STARTED = 1;
	public final static int MSG_WHAT_ERROR = 1;
	public final static String KEY_ERROR = "Key.Error";
	
	private final static String TAG = "JPEGVRecorder";
	private static class FrameInformation {
		private byte[] nv21;
		private long sysTime;
	}
	
	private static class ReplyHandler extends Handler {
		private JpegVideoRecorder p;
		public ReplyHandler(JpegVideoRecorder parent) {
			this.p = parent;
		}
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_WHAT_ERROR:
				if (p.onErrorListener != null) {
					Bundle b = msg.getData();
					Throwable e = (Throwable) b.getSerializable(KEY_ERROR);
					p.onErrorListener.onError(p, e);
				}
				break;
			default:
				super.handleMessage(msg);
				break;
			}
			msg.recycle();
		}
	}
	
	private ReusePool<byte[]> reuseNv21 = new ReusePool<byte[]>();
	
	private boolean recording;
	private Context context;
	private OnErrorListener<JpegVideoRecorder> onErrorListener;
	private OnInfoListener<JpegVideoRecorder> onInfoListener;
	
	private Queue<FrameInformation> processQ = new LinkedList<FrameInformation>();
	
	private int width;
	private int height;
	private Rect rect = new Rect();
	
	private int format = ImageFormat.NV21;
	private int quality = 100;
	
	private long prevTime;
	
	private long interval;
	
	private File path;
	
	private IFrameTransporter transporter;
	private Messenger replyTo = new Messenger(new ReplyHandler(this));
	private ServiceConnection connection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			transporter = IFrameTransporter.Stub.asInterface(service);
			startProcessThread();
		}
		@Override
		public void onServiceDisconnected(ComponentName name) {
			transporter = null;
		}
	};
	
	private Runnable frameProcess = new Runnable() {
		private long prevSentTime;
		private int prevDataLength;
		
		@Override
		public void run() {
			int count = 0;
			long startTime = -1;
			long endTime = -1;
			try {
				while (recording) {
					while (recording && processQ.isEmpty()) {
						Thread.yield();
					}
					
					while (!processQ.isEmpty()) {
						FrameInformation frame = null;
						synchronized (processQ) {
							frame = processQ.poll();
							Log.d(TAG, "Q len:" + processQ.size());
						}
						if (frame != null) {
							if (startTime < 0) {
								startTime = frame.sysTime;
							}
							
							// the time past from last sent
							long dt = frame.sysTime - prevSentTime;
							// the max possible transfer data size during this period
							long maxDataLimit = Constants.MAX_DISK_BPMS * dt;
							
							if (this.prevDataLength < maxDataLimit || this.prevDataLength == 0) {
								endTime = frame.sysTime;
								this.prevDataLength = frame.nv21.length;
								this.prevSentTime = frame.sysTime;
								File file = new File(path, String.format("Frame_%010d.jpg", count++));
								sendFile4Writing(frame, file.getAbsolutePath());
								reuseNv21.recycle(frame.nv21);
								frame = null;
							}
						}
					}
				}
			} catch (Throwable e) {
				if (onErrorListener != null) {
					onErrorListener.onError(JpegVideoRecorder.this, e);
				} else {
					e.printStackTrace();
				}
			} finally {
				synchronized(processQ) {
					processQ.clear();
				}
				unbindFileService();
				this.writeInformation2File(count, endTime - startTime);
				this.prevDataLength = 0;
				this.prevSentTime = 0;
			}
		}

		private void writeInformation2File(int count, long dt) {
			double fps = count * 1000. / dt;
			File file = new File(path, VideoInformation.INFO_FILENAME);
			PrintStream out = null;
			try {
				out = new PrintStream(file);
				out.println(String.format("w:%d", width));
				out.println(String.format("h:%d", height));
				out.println(String.format("fps:%f", fps));
				out.println(String.format("quality:%d", quality));
				out.flush();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} finally {
				if (out != null) {
					out.close();
				}
			}
		}
	};
	
	private void sendFile4Writing(FrameInformation frame, String filename) throws RemoteException {
		ParcelableFrame pf = new ParcelableFrame();
		pf.setFilename(filename);
		pf.setData(frame.nv21);
		pf.setQuality(quality);
		pf.setFormat(format);
		pf.setWidth(width);
		pf.setHeight(height);
		pf.setReplyTo(replyTo);
		if (this.transporter != null) {
			this.transporter.sendFrame(pf);
		}
	}
	
	private void bindFileService() {
		Intent intent = new Intent(context, ImageSaveService.class);
		context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
	}
	
	private void unbindFileService() {
		if (this.transporter != null) {
			context.unbindService(connection);
		}
	}
	
	private void startProcessThread() {
		this.recording = true;
		Thread t = new Thread(this.frameProcess, "JPEG Record Thread");
		t.setDaemon(true);
		t.start();
		if (this.onInfoListener != null) {
			this.onInfoListener.onInfo(this, INFO_WHAT_RECORD_STARTED, recording ? 1 : 0);
		}
	}
	
	public JpegVideoRecorder(Context context) {
		this.context = context;
	}
	
	public void setOutputFilePath(File path) {
		this.path = path;
	}
	
	public void setVideoSize(int width, int height) {
		this.width = width;
		this.height = height;
		this.rect.set(0, 0, width, height);
	}
	
	public void setFPS(int fps) {
		Log.d(TAG, "FPS: " + fps);
		this.interval = 1000 / fps;
	}
	
	public void setFrameFormat(int format) {
		this.format = format;
	}
	
	public void setVideoQuality(int quality) {
		this.quality = quality;
	}
	
	public void startRecord() {
		this.bindFileService();
	}
	
	public void putFrame(byte[] nv21, long time) {
		Log.d(TAG, "raw len:" + nv21.length);
		if (time - this.prevTime > this.interval) {
			FrameInformation frame = new FrameInformation();
			frame.nv21 = this.reuseNv21.get();
			if (frame.nv21 == null) {
				frame.nv21 = new byte[nv21.length];
			}
			System.arraycopy(nv21, 0, frame.nv21, 0, frame.nv21.length);
			nv21 = null;
			frame.sysTime = time;
			synchronized (this.processQ) {
				this.processQ.offer(frame);
			}
			this.prevTime = time;
		}
	}
	
	public void stopRecord() {
		Log.d(TAG, "stop record");
		this.recording = false;
	}
	
	public boolean isRecording() {
		return this.recording;
	}

	public OnErrorListener<JpegVideoRecorder> getOnErrorListener() {
		return onErrorListener;
	}

	public void setOnErrorListener(OnErrorListener<JpegVideoRecorder> onErrorListener) {
		this.onErrorListener = onErrorListener;
	}

	public OnInfoListener<JpegVideoRecorder> getOnInfoListener() {
		return onInfoListener;
	}

	public void setOnInfoListener(OnInfoListener<JpegVideoRecorder> onInfoListener) {
		this.onInfoListener = onInfoListener;
	}
}
