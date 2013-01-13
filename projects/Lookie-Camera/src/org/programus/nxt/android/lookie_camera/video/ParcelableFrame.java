package org.programus.nxt.android.lookie_camera.video;

import android.os.Messenger;
import android.os.Parcel;
import android.os.Parcelable;

public class ParcelableFrame implements Parcelable {
	private byte[] data;
	private String filename;
	private int quality;
	private int format;
	private int width;
	private int height;
	
	private Messenger replyTo;
	
	public ParcelableFrame() {
	}
	
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(filename);
		dest.writeInt(quality);
		dest.writeInt(format);
		dest.writeInt(width);
		dest.writeInt(height);
		dest.writeByteArray(data);
	}
	
	private ParcelableFrame(Parcel in) {
		this.filename = in.readString();
		this.quality = in.readInt();
		this.format = in.readInt();
		this.width = in.readInt();
		this.height = in.readInt();
		this.data = in.createByteArray();
	}
	
	public static final Parcelable.Creator<ParcelableFrame> CREATOR = new Parcelable.Creator<ParcelableFrame>() {
		@Override
		public ParcelableFrame createFromParcel(Parcel source) {
			return new ParcelableFrame(source);
		}
		@Override
		public ParcelableFrame[] newArray(int size) {
			return new ParcelableFrame[size];
		}
	};

	public byte[] getData() {
		return data;
	}

	public void setData(byte[] data) {
		this.data = data;
	}

	public String getFilename() {
		return filename;
	}

	public void setFilename(String filename) {
		this.filename = filename;
	}

	public int getQuality() {
		return quality;
	}

	public void setQuality(int quality) {
		this.quality = quality;
	}

	public int getFormat() {
		return format;
	}

	public void setFormat(int format) {
		this.format = format;
	}

	public int getWidth() {
		return width;
	}

	public void setWidth(int width) {
		this.width = width;
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
	}

	public Messenger getReplyTo() {
		return replyTo;
	}

	public void setReplyTo(Messenger replyTo) {
		this.replyTo = replyTo;
	}
}
