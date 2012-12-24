package org.programus.nxj.lookie.nxt.comm;

import org.programus.lookie.lib.comm.CommandMessage;
import org.programus.lookie.lib.utils.Constants;
import org.programus.lookie.lib.utils.FixedLengthQueue;
import org.programus.lookie.lib.utils.SimpleQueue;

public class DataBuffer {
	private SimpleQueue<CommandMessage>[] readQueues;
	
	private static DataBuffer db = new DataBuffer();
	@SuppressWarnings("unchecked")
	private DataBuffer() {
		this.readQueues = new SimpleQueue[3];
		for (int i = 0; i < this.readQueues.length; i++) {
			this.readQueues[i] = new FixedLengthQueue<CommandMessage>(Constants.READ_Q_SIZE);
		}
	}
	
	public static DataBuffer getInstance() {
		return db;
	}

	public SimpleQueue<CommandMessage>[] getReadQueues() {
		return readQueues;
	}
}
