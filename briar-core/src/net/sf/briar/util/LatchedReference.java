package net.sf.briar.util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class LatchedReference<T> {

	private final CountDownLatch latch = new CountDownLatch(1);
	private final AtomicReference<T> reference = new AtomicReference<T>();

	public boolean isSet() {
		return reference.get() != null;
	}

	public boolean set(T t) {
		if(t == null) throw new IllegalArgumentException();
		if(reference.compareAndSet(null, t)) {
			latch.countDown();
			return true;
		}
		return false;
	}

	public T waitForReference(long timeout) throws InterruptedException {
		latch.await(timeout, TimeUnit.MILLISECONDS);
		return reference.get();
	}
}
