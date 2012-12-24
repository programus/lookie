package org.programus.nxt.android.lookie_rc.comm;

import java.io.Serializable;

public class NXTData implements Serializable {
	private static final long serialVersionUID = 1897222538921137293L;
	
	private int dir;
	private float speed;
	
	public int getDir() {
		return dir;
	}
	public void setDir(int dir) {
		this.dir = dir;
	}
	public float getSpeed() {
		return speed;
	}
	public void setSpeed(float speed) {
		this.speed = speed;
	}
	
	
}
