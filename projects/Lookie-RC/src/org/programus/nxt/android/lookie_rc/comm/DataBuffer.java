package org.programus.nxt.android.lookie_rc.comm;

import java.lang.reflect.Array;

import org.programus.lookie.lib.comm.CameraCommand;
import org.programus.lookie.lib.comm.CommandMessage;
import org.programus.lookie.lib.utils.Constants;
import org.programus.lookie.lib.utils.FixedLengthQueue;
import org.programus.lookie.lib.utils.SimpleQueue;

public class DataBuffer {
	private SimpleQueue<CommandMessage>[] sendQueues;
	private SimpleQueue<CameraCommand> camSendQ;
	
	private static DataBuffer db = new DataBuffer();
	@SuppressWarnings("unchecked")
	private DataBuffer() {
		SimpleQueue<CommandMessage> q = new FixedLengthQueue<CommandMessage>(0);
		this.sendQueues = (SimpleQueue<CommandMessage>[]) Array.newInstance(q.getClass(), 3);
		for (int i = 0; i < this.sendQueues.length; i++) {
			this.sendQueues[i] = new FixedLengthQueue<CommandMessage>(Constants.SEND_Q_SIZE);
		}
		this.camSendQ = new FixedLengthQueue<CameraCommand>(Constants.SEND_Q_SIZE);
	}
	
	public static DataBuffer getInstance() {
		return db;
	}

	public SimpleQueue<CommandMessage>[] getSendQueues() {
		return sendQueues;
	}

	public SimpleQueue<CameraCommand> getCamSendQueue() {
		return this.camSendQ;
	}
}
