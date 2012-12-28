package org.programus.lookie.lib.comm;

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
