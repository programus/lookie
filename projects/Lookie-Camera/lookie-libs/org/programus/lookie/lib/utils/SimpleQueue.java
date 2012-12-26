package org.programus.lookie.lib.utils;

public interface SimpleQueue<E> {
	boolean offer(E e);
	E poll();
	E peek();
	int size();
	boolean isEmpty();
	void clear();
}
