package org.programus.nxt.android.lookie_camera.video;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.Queue;

import org.programus.lookie.lib.utils.Constants;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.util.Log;

public class JpegVideoRecorder {
	private final static String TAG = "JPEGVRecorder";
	private final static String INFO_FILENAME = "info.txt";
	private static class FrameInformation {
		private byte[] nv21;
		private long sysTime;
	}
	
	private boolean recording;
	
	private Queue<FrameInformation> processQ = new LinkedList<FrameInformation>();
	
	private int width;
	private int height;
	private Rect rect = new Rect();
	
	private int format = ImageFormat.NV21;
	private int quality = 100;
	
	private long prevTime;
	
	private long interval;
	
	private File path;
	
	private Runnable frameProcess = new Runnable() {
		private long prevSentTime;
		private int prevDataLength;
		
		@Override
		public void run() {
			int count = 0;
			long startTime = -1;
			long endTime = -1;
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
							this.writeFrame2File(frame.nv21, String.format("Frame_%010d.jpg", count++));
							this.prevDataLength = frame.nv21.length;
							this.prevSentTime = frame.sysTime;
						}
						frame = null;
					}
				}
			}
			this.writeInformation2File(count, endTime - startTime);
			this.prevDataLength = 0;
			this.prevSentTime = 0;
		}
		
		private void writeFrame2File(final byte[] nv21, final String filename) {
			Thread t = new Thread(new Runnable() {
				@Override
				public void run() {
					File file = new File(path, filename);
					Log.d(TAG, String.format("Write to file: %s", file.getAbsolutePath()));
					FileOutputStream out = null;
					try {
						out = new FileOutputStream(file);
						YuvImage image = new YuvImage(nv21, format, width, height, null);
						image.compressToJpeg(rect, quality, out);
						image = null;
						out.flush();
						Log.d(TAG, String.format("complete file: %s", file.getAbsolutePath()));
					} catch (IOException e) {
						e.printStackTrace();
					} finally {
						if (out != null) {
							try {
								out.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}
				}
			}, "Write 2 file thread");
			t.start();
		}
		
		private void writeInformation2File(int count, long dt) {
			double fps = count * 1000 / dt;
			File file = new File(path, INFO_FILENAME);
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
		this.recording = true;
		Thread t = new Thread(this.frameProcess, "JPEG Record Thread");
		t.setDaemon(true);
		t.start();
	}
	
	public void putFrame(byte[] nv21, long time) {
		if (time - this.prevTime > this.interval) {
			FrameInformation frame = new FrameInformation();
			frame.nv21 = nv21;
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
}
