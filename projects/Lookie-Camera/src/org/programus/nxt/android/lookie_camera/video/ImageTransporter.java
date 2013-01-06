package org.programus.nxt.android.lookie_camera.video;

import java.util.LinkedList;
import java.util.Queue;

import org.programus.lookie.lib.comm.CameraCommand;
import org.programus.lookie.lib.utils.Constants;
import org.programus.lookie.lib.utils.SimpleQueue;

import android.util.Log;

public class ImageTransporter {
	private final static String TAG = "ImageTransporter";
	private static class ImageInformation {
		private int srcWidth;
		private int srcHeight;
		private int dstWidth;
		private int dstHeight;
		private byte[] nv21;
		private int format;
		private int quality;
		private long sysTime;
		
		private CameraCommand cmd;
		
		public byte[] getImageDataForSend() {
			if (nv21 == null) {
				return null;
			}
			Log.d(TAG, String.format("nv21.length: %d / src: %d x %d / dst: %d x %d", nv21.length, srcWidth, srcHeight, dstWidth, dstHeight));
//			byte[] scaled = ImageUtilities.scaleNV21Image(nv21, srcWidth, srcHeight, dstWidth, dstHeight);
			return ImageUtilities.compressYuvImage2GzipJpeg(nv21, format, quality, dstWidth, dstHeight);
		}
		
		public CameraCommand getCommand() {
			if (this.cmd == null) {
				cmd = new CameraCommand();
				cmd.setCommand(Constants.CAMERA);
				cmd.setFormat(format);
				cmd.setWidth(dstWidth);
				cmd.setHeight(dstHeight);
				cmd.setImageData(this.getImageDataForSend());
				cmd.setSystemTime(sysTime);
			}
			return this.cmd;
		}
	}
	
	private SimpleQueue<CameraCommand> sendQ;
	
	private ReusePool<byte[]> reuseNv21 = new ReusePool<byte[]>();
	
	private Queue<ImageInformation> processQ = new LinkedList<ImageInformation>();
	
	private OnErrorListener onErrorListener;
	
	private boolean processing;
	
	private Runnable frameProcessor = new Runnable() {
		private long prevSentTime;
		private int prevDataLength;
		
		@Override
		public void run() {
			try {
				while (processing) {
					while (processing && processQ.isEmpty()) {
						Thread.yield();
					}
					
					while (!processQ.isEmpty()) {
						ImageInformation ii = null;
						synchronized (processQ) {
							ii = processQ.poll();
						}
						if (ii != null) {
							this.sendImage(ii);
							ii = null;
						}
						Thread.yield();
					}
				}
				this.prevDataLength = 0;
				this.prevSentTime = 0;
			} catch (Throwable e) {
				if (onErrorListener != null) {
					onErrorListener.onError(e);
				} else {
					e.printStackTrace();
				}
			}
		}
		
		private void sendImage(ImageInformation ii) {
			// the time past from last sent
			long dt = ii.sysTime - prevSentTime;
			// the max possible transfer data size during this period
			long maxDataLimit = Constants.MAX_BPMS * dt;
			
			Log.d(TAG, String.format("dt: %d, max: %d, size: %d", dt, maxDataLimit, this.prevDataLength));
			if (this.prevDataLength < maxDataLimit || this.prevDataLength == 0) {
				CameraCommand cmd = ii.getCommand();
				synchronized (sendQ) {
					Log.d(TAG, String.format("time: %d, len: %d", cmd.getSystemTime(), cmd.getImageData().length));
					sendQ.offer(cmd);
				}
				this.prevDataLength = cmd.getImageData().length;
				this.prevSentTime = ii.sysTime;
			} else {
				ii = null;
				Log.d(TAG, "skip a frame");
			}
		}
	};
	
	public ImageTransporter(SimpleQueue<CameraCommand> sendQ) {
		this.sendQ = sendQ;
	}
	
	public void start() {
		this.processing = true;
		
		Thread t = new Thread(this.frameProcessor, "frame process thread");
		t.setDaemon(true);
		t.start();
	}
	
	public void transportFrame(byte[] nv21, int srcWidth, int srcHeight, int dstWidth, int dstHeight, long time, int format, int quality) {
		byte[] buff = this.reuseNv21.get();
		if (buff == null) {
			buff = new byte[nv21.length];
		}
		System.arraycopy(nv21, 0, buff, 0, nv21.length);
		nv21 = null;
		ImageInformation ii = new ImageInformation();
		ii.srcWidth = srcWidth;
		ii.srcHeight = srcHeight;
		ii.dstWidth = dstWidth;
		ii.dstHeight = dstHeight;
		ii.nv21 = ImageUtilities.scaleNV21Image(buff, srcWidth, srcHeight, dstWidth, dstHeight);
		ii.format = format;
		ii.quality = quality;
		ii.sysTime = time;
		
		this.reuseNv21.recycle(buff);
		synchronized (this.processQ) {
			this.processQ.offer(ii);
		}
	}
	
	public void stop() {
		this.processing = false;
	}

	public OnErrorListener getOnErrorListener() {
		return onErrorListener;
	}

	public void setOnErrorListener(OnErrorListener onErrorListener) {
		this.onErrorListener = onErrorListener;
	}
}
