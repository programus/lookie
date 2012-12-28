package org.programus.nxt.android.lookie_camera.comm;

import org.programus.lookie.lib.comm.CameraCommand;
import org.programus.lookie.lib.utils.Constants;
import org.programus.lookie.lib.utils.FixedLengthQueue;
import org.programus.lookie.lib.utils.SimpleQueue;

public class DataBuffer {
	private SimpleQueue<CameraCommand> sendQueue;
	private SimpleQueue<CameraCommand> readQueue;
	
	private static DataBuffer db = new DataBuffer();
	
	private DataBuffer() {
		this.sendQueue = new FixedLengthQueue<CameraCommand>(Constants.SEND_Q_SIZE);
		this.readQueue = new FixedLengthQueue<CameraCommand>(Constants.READ_Q_SIZE);
	}
	
	public static DataBuffer getInstance() {
		return db;
	}

	public SimpleQueue<CameraCommand> getSendQueue() {
		return sendQueue;
	}

	public SimpleQueue<CameraCommand> getReadQueue() {
		return readQueue;
	}
}
