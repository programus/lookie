package org.programus.nxt.android.lookie_camera.utils;

import org.programus.lookie.lib.utils.Constants;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

public class Logger {
	private Handler handler;
	private final static Logger inst = new Logger();
	
	private Logger() {}
	
	public static Logger getInstance() {
		return inst;
	}
	
	public void setHandler(Handler handler) {
		this.handler = handler;
	}
	
	public void log(String text) {
		if (this.handler != null) {
			Message msg = new Message();
			msg.what = Constants.MSG_WHAT_LOG;
			
			Bundle b = new Bundle();
			b.putString(Constants.KEY_LOG, text);
			
			msg.setData(b);
			
			this.handler.sendMessage(msg);
		}
	}
}
