package org.briarproject.bramble.io;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.Clock;

import java.io.IOException;
import java.io.InputStream;

import javax.annotation.concurrent.GuardedBy;

@NotNullByDefault
class TimeoutInputStream extends InputStream {

	private final Clock clock;
	private final InputStream in;
	private final long timeoutNs;
	private final CloseListener listener;
	private final Object lock = new Object();
	@GuardedBy("lock")
	private long readStartedNs = -1;

	TimeoutInputStream(Clock clock, InputStream in, long timeoutNs,
			CloseListener listener) {
		this.clock = clock;
		this.in = in;
		this.timeoutNs = timeoutNs;
		this.listener = listener;
	}

	@Override
	public int read() throws IOException {
		synchronized (lock) {
			readStartedNs = clock.nanoTime();
		}
		int input = in.read();
		synchronized (lock) {
			readStartedNs = -1;
		}
		return input;
	}

	@Override
	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		synchronized (lock) {
			readStartedNs = clock.nanoTime();
		}
		int read = in.read(b, off, len);
		synchronized (lock) {
			readStartedNs = -1;
		}
		return read;
	}

	@Override
	public void close() throws IOException {
		try {
			in.close();
		} finally {
			listener.onClose(this);
		}
	}

	boolean hasTimedOut() {
		synchronized (lock) {
			return readStartedNs != -1 &&
					clock.nanoTime() - readStartedNs > timeoutNs;
		}
	}

	interface CloseListener {

		void onClose(TimeoutInputStream closed);
	}
}
