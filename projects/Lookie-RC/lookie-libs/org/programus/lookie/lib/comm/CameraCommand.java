package org.programus.lookie.lib.comm;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;

public class CameraCommand implements Serializable {
	private static final long serialVersionUID = -7006390932100119499L;
	
	private int command;
	private float angle;
	private long systemTime;
	private byte[] imageData;
	private int format;
	private int width;
	private int height;
	
	public int getCommand() {
		return command;
	}
	public void setCommand(int command) {
		this.command = command;
	}
	public float getAngle() {
		return angle;
	}
	public void setAngle(float angle) {
		this.angle = angle;
	}
	public byte[] getImageData() {
		return imageData;
	}
	public void setImageData(byte[] imageData) {
		this.imageData = imageData;
	}
	public long getSystemTime() {
		return systemTime;
	}
	public void setSystemTime(long systemTime) {
		this.systemTime = systemTime;
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
	public int getFormat() {
		return format;
	}
	public void setFormat(int format) {
		this.format = format;
	}
	
	public void write(DataOutputStream out) throws IOException {
		out.writeInt(command);
		out.writeLong(systemTime);
		out.writeFloat(angle);
		out.writeInt(format);
		out.writeInt(width);
		out.writeInt(height);
		out.writeInt(imageData == null ? 0 : imageData.length);
		if (imageData != null) {
			out.write(imageData, 0, imageData.length);
		}
		out.flush();
	}
	
	public void read(DataInputStream in) throws IOException {
		this.command = in.readInt();
		this.systemTime = in.readLong();
		this.angle = in.readFloat();
		this.format = in.readInt();
		this.width = in.readInt();
		this.height = in.readInt();
		int len = in.readInt();
		if (len > 0) {
			this.imageData = new byte[len];
			in.read(imageData, 0, len);
		} else {
			this.imageData = null;
		}
	}
	
	public static CameraCommand readNew(DataInputStream in) throws IOException {
		CameraCommand cmd = new CameraCommand();
		cmd.read(in);
		return cmd;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb
		.append("CMD: ")
		.append(command)
		.append("/ angle:")
		.append(angle)
		.append("/ time: ")
		.append(systemTime);
		
		if (this.imageData != null) {
			sb
			.append("/ (").append(this.width).append(", ").append(this.height).append(")")
			.append("/ data len: ").append(this.imageData.length);
		}
		return sb.toString();
	}
}
