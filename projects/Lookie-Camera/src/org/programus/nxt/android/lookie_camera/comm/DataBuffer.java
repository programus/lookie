package org.programus.nxt.android.lookie_camera.comm;

import java.util.LinkedList;
import java.util.Queue;

import org.programus.lookie.lib.comm.CameraCommand;
import org.programus.lookie.lib.utils.Constants;
import org.programus.lookie.lib.utils.FixedLengthQueue;
import org.programus.lookie.lib.utils.SimpleQueue;

public class DataBuffer {
	private Queue<CameraCommand> sendQueue;
	private SimpleQueue<CameraCommand> imageQueue;
	private SimpleQueue<CameraCommand> readQueue;
	
	private static DataBuffer db = new DataBuffer();
	
	private DataBuffer() {
		this.sendQueue = new LinkedList<CameraCommand>();
		this.imageQueue = new FixedLengthQueue<CameraCommand>(Constants.SEND_Q_SIZE);
		this.readQueue = new FixedLengthQueue<CameraCommand>(Constants.READ_Q_SIZE);
	}
	
	public static DataBuffer getInstance() {
		return db;
	}

	public Queue<CameraCommand> getSendQueue() {
		return sendQueue;
	}
	
	public SimpleQueue<CameraCommand> getImageQueue() {
		return imageQueue;
	}

	public SimpleQueue<CameraCommand> getReadQueue() {
		return readQueue;
	}
}
