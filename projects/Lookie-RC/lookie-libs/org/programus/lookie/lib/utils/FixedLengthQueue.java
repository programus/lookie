package org.programus.lookie.lib.utils;

import java.util.Arrays;

public class FixedLengthQueue<E> implements SimpleQueue<E> {
	private Object[] data;
	private int inp;
	private int outp;
	
	private int fixp(int p) {
		if (p < 0) {
			p += p % data.length;
		} else if (p >= data.length) {
			p = p % data.length;
		}
		return p;
	}
	
	public FixedLengthQueue(int capacity) {
		this.data = new Object[capacity + 1];
	}

	@Override
	public boolean offer(E e) {
		synchronized(this) {
			boolean ret = true;
			data[inp++] = e;
			inp = fixp(inp);
			if (inp == outp) {
				outp = fixp(outp + 1);
				ret = false;
			}
			return ret;
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public E poll() {
		synchronized(this) {
			E ret = null;
			if (!this.isEmpty()) {
				ret = (E) data[outp++];
				outp = fixp(outp);
			}
			return ret;
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public E peek() {
		synchronized(this) {
			return (E) data[outp];
		}
	}

	@Override
	public int size() {
		synchronized(this) {
			int ret = inp - outp;
			if (ret < 0) {
				ret += data.length;
			}
			return ret;
		}
	}

	@Override
	public boolean isEmpty() {
		synchronized(this) {
			return inp == outp;
		}
	}
	
	@Override
	public String toString() {
		synchronized(this) {
			StringBuffer sb = new StringBuffer();
			sb.append("<< ");
			if (!this.isEmpty()) {
				for (int i = outp; i != inp; i = fixp(i + 1)) {
					sb.append(data[i]);
					sb.append(", ");
				}
				sb.deleteCharAt(sb.length() - 2);
			}
			sb.append("<<");
			return sb.toString();
		}
	}
	
	public static void main(String[] args) {
		SimpleQueue<Integer> q = new FixedLengthQueue<Integer>(1);
		for (int i = 0; i < 3; i++) {
			q.offer(i);
			System.out.println(q);
		}
		for (int i = 0; i < 3; i++) {
			System.out.println(q.poll());
			System.out.println(q);
		}
		
	}

	@Override
	public void clear() {
		this.inp = this.outp = 0;
		Arrays.fill(data, null);
	}
}
