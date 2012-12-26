package org.programus.lookie.lib.comm;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class CommandMessage {
	private int command;
	private float data;
	
	public int getCommand() {
		return command;
	}
	public void setCommand(int command) {
		this.command = command;
	}
	public float getData() {
		return data;
	}
	public void setData(float data) {
		this.data = data;
	}
	
	public void send(DataOutputStream out) throws IOException {
		out.writeInt(command);
		out.writeFloat(data);
	}
	
	public static CommandMessage read(DataInputStream in) throws IOException {
		CommandMessage cmd = new CommandMessage();
		cmd.setCommand(in.readInt());
		cmd.setData(in.readFloat());
		return cmd;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb
		.append("CMD: ")
		.append(this.command)
		.append("/Data: ")
		.append(this.data);
		return sb.toString();
	}
}
