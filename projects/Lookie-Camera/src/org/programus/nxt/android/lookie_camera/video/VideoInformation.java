package org.programus.nxt.android.lookie_camera.video;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

public class VideoInformation implements Serializable{
	private static final long serialVersionUID = -6596108845433074639L;
	private static final String TAG = "VidInfo";
	
	public final static String VIDEO_PATH = "Lookie_Record";
	public final static String INFO_FILENAME = "info.txt";
	public final static String AUDIO_FILENAME = "audio.3gp";
	
	private String name;
	private int width;
	private int height;
	private double fps;
	private int frames;
	private int quality;
	
	private File path;
	private File[] images;
	private File audioFile;
	
	private transient Bitmap previewImage;
	
	private static FilenameFilter jpgFilter = new FilenameFilter() {
		@Override
		public boolean accept(File dir, String filename) {
			return filename.toLowerCase(Locale.ENGLISH).endsWith(".jpg");
		}
	};
	
	private static FileFilter vidPathFilter = new FileFilter() {
		@Override
		public boolean accept(File pathname) {
			boolean ret = pathname.isDirectory();
			if (ret) {
				File infoFile = new File(pathname, INFO_FILENAME);
				ret = infoFile.exists();
			}
			return ret;
		}
	};
	
	public static List<VideoInformation> getVideoList() {
		List<VideoInformation> videos = null;
		if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
			File path = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), VIDEO_PATH);
			if (path.exists()) {
				File[] vids = path.listFiles(vidPathFilter);
				Arrays.sort(vids);
				if (vids != null) {
					videos = new ArrayList<VideoInformation>(vids.length);
					for (int i = 0; i < vids.length; i++) {
						try {
							videos.add(new VideoInformation(vids[i]));
						} catch (IOException e) {
						}
					}
				}
			}
		}
		return videos;
	}
	
	public VideoInformation(String pathname) throws IOException {
		this(new File(pathname));
	}
	
	public VideoInformation(File path) throws IOException {
		File infoFile = new File(path, INFO_FILENAME);
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(infoFile));
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				String[] info = line.split(":");
				if (info.length > 1) {
					String k = info[0];
					String v = info[1];
					if ("w".equals(k)) {
						this.width = Integer.parseInt(v);
					} else if ("h".equals(k)) {
						this.height = Integer.parseInt(v);
					} else if ("fps".equals(k)) {
						this.fps = Double.parseDouble(v);
					} else if ("quality".equals(k)) {
						this.quality = Integer.parseInt(v);
					} else if ("frames".equals(k)) {
						this.frames = Integer.parseInt(v);
					}
				}
			}
		} catch (NumberFormatException e) {
			e.printStackTrace();
			throw e;
		} catch (IOException e) {
			e.printStackTrace();
			throw e;
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		this.path = path;
		this.name = path.getName();
		this.images = path.listFiles(jpgFilter);
		this.audioFile = new File(path, AUDIO_FILENAME);
		Arrays.sort(this.images);
		if (this.frames == 0 && this.images != null) {
			this.frames = this.images.length;
		}
	}
	
	public String getName() {
		return name;
	}
	public int getWidth() {
		return width;
	}
	public int getHeight() {
		return height;
	}
	public double getFps() {
		return fps;
	}
	public int getFrames() {
		return frames;
	}
	public int getQuality() {
		return quality;
	}

	public File getPath() {
		return path;
	}

	public File[] getImages() {
		return images;
	}
	
	public File getAudioFile() {
		return audioFile;
	}
	
	public Bitmap getPreviewImage() {
		if (this.previewImage == null) {
			if (this.images != null && this.images.length > 0) {
				File img = this.images[0];
				BitmapFactory.Options opts = new BitmapFactory.Options();
				opts.inDither = false;
				opts.inInputShareable = true;
				opts.inPurgeable = true;
				opts.inScaled = true;
				opts.inSampleSize = 4;
				Bitmap bmp = BitmapFactory.decodeFile(img.getAbsolutePath(), opts);
				if (bmp != null) {
					Log.d(TAG, String.format("bmp size: %d x %d", bmp.getWidth(), bmp.getHeight()));
					this.previewImage = Bitmap.createScaledBitmap(bmp, this.width >> 2, this.height >> 2, true);
					bmp.recycle();
				}
			}
		}
		return this.previewImage;
	}
}
