package org.programus.nxt.android.lookie_camera.video;

import java.lang.ref.WeakReference;
import java.util.LinkedList;

public class ReusePool<T> {
	private LinkedList<WeakReference<T>> pool = new LinkedList<WeakReference<T>>();
	
	public T get() {
		T ret = null;
		synchronized (pool) {
			while (!pool.isEmpty()) {
				ret = pool.pop().get();
				if (ret != null) {
					break;
				}
			}
		}
		return ret;
	}
	
	public void recycle(T e) {
		WeakReference<T> r = new WeakReference<T>(e);
		synchronized (pool) {
			pool.push(r);
		}
	}
}
