package org.programus.nxt.android.lookie_camera.services;

import java.io.FileOutputStream;
import java.io.IOException;

import org.programus.nxt.android.lookie_camera.activites.MainActivity;
import org.programus.nxt.android.lookie_camera.video.ImageUtilities;
import org.programus.nxt.android.lookie_camera.video.JpegVideoRecorder;
import org.programus.nxt.android.lookie_camera.video.ParcelableFrame;
import org.programus.nxt.android.lookie_camera.video.ReusePool;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class ImageSaveService extends Service {
	private final static String TAG = "SaveFrameSvc";
	private final static String THREAD_GROUP_NAME = "Image file write threads";
	
	private int notificationId = 5;
	private Notification notification;
	
	private ThreadGroup group;
	
	private ReusePool<byte[]> reuseNv21 = new ReusePool<byte[]>();
	
	private IFrameTransporter.Stub transporter = new IFrameTransporter.Stub() {
		@Override
		public void sendFrame(ParcelableFrame pf) throws RemoteException {
			startFileWriting(pf);
		}
	};

	private void runForground() {
		if (this.notification == null) {
			Intent intent = new Intent(this, MainActivity.class);
			PendingIntent pintent = PendingIntent.getActivity(this, 0, intent, 0);
			notification = new NotificationCompat.Builder(this.getApplicationContext())
				.setContentTitle("Video Writing")
				.setContentText("Writing frames of video to card.")
				.setSmallIcon(android.R.drawable.presence_video_busy)
				.setContentIntent(pintent)
				.build();
		}
		this.startForeground(notificationId, notification);
	}
	
	private void initComponents() {
		this.group = new ThreadGroup(THREAD_GROUP_NAME);
	}
	
	private void writeFrame2File(byte[] nv21, final String filename, final int format, final int quality, final int width, final int height, final ThreadGroup group, final Messenger replyTo) {
		final byte[][] nv21Holder = new byte[1][];
		nv21Holder[0] = nv21;
		nv21 = null;
		Thread t = new Thread(group, new Runnable() {
			@Override
			public void run() {
				Log.d(TAG, "Files: " + group.activeCount());
				try {
					Log.d(TAG, String.format("Write to file: %s", filename));
					FileOutputStream out = null;
					try {
						out = new FileOutputStream(filename);
						byte[] jpg = ImageUtilities.compressYuvImage2Jpeg(nv21Holder[0], format, quality, width, height);
						reuseNv21.recycle(nv21Holder[0]);
						nv21Holder[0] = null;
						out.write(jpg);
						jpg = null;
						out.flush();
						Log.d(TAG, String.format("complete file: %s", filename));
					} catch (IOException e) {
						if (!sendError(replyTo, e)) {
							e.printStackTrace();
						}
					} finally {
						if (out != null) {
							try {
								out.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}
				} catch (Throwable e) {
					if (!sendError(replyTo, e)) {
						e.printStackTrace();
					}
				}
			}
		}, "Write 2 file thread");
		t.start();
	}
	
	private void startFileWriting(ParcelableFrame pf) {
		if (pf != null) {
			String filename = pf.getFilename();
			byte[] nv21 = pf.getData();
			int quality = pf.getQuality();
			int format = pf.getFormat();
			int width = pf.getWidth();
			int height = pf.getHeight();
			Messenger replyTo = pf.getReplyTo();
			pf = null;
			
			Log.d(TAG, "Started file writing: " + filename + "/" + nv21);
				
			try {
				if (nv21 != null) {
					byte[] data = reuseNv21.get();
					if (data == null) {
						data = new byte[nv21.length];
					}
					System.arraycopy(nv21, 0, data, 0, data.length);
					nv21 = null;
					this.writeFrame2File(data, filename, format, quality, width, height, group, replyTo);
				}
			} catch (Throwable e) {
				if (!this.sendError(replyTo, e)) {
					e.printStackTrace();
				}
			}
		}
	}
	
	private boolean sendError(Messenger replyTo, Throwable e) {
		boolean ret = false;
		if (replyTo != null) {
			Message msg = Message.obtain(null, JpegVideoRecorder.MSG_WHAT_ERROR);
			Bundle b = new Bundle();
			b.putSerializable(JpegVideoRecorder.KEY_ERROR, e);
			msg.setData(b);
			try {
				replyTo.send(msg);
				ret = true;
			} catch (RemoteException ex) {
				ret = false;
				ex.printStackTrace();
			}
		}
		return ret;
	}
		
	@Override
	public IBinder onBind(Intent intent) {
//		return this.messenger.getBinder();
		return this.transporter;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		this.initComponents();
		this.runForground();
		Log.d(TAG, "Image Writing Service created.");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "Image Writing Service destroyed.");
	}
}
