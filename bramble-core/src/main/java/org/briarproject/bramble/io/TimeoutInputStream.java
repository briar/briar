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
	private final long timeoutMs;
	private final CloseListener listener;
	private final Object lock = new Object();
	@GuardedBy("lock")
	private long readStartedMs = -1;

	TimeoutInputStream(Clock clock, InputStream in, long timeoutMs,
			CloseListener listener) {
		this.clock = clock;
		this.in = in;
		this.timeoutMs = timeoutMs;
		this.listener = listener;
	}

	@Override
	public int read() throws IOException {
		synchronized (lock) {
			readStartedMs = clock.currentTimeMillis();
		}
		int input = in.read();
		synchronized (lock) {
			readStartedMs = -1;
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
			readStartedMs = clock.currentTimeMillis();
		}
		int read = in.read(b, off, len);
		synchronized (lock) {
			readStartedMs = -1;
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

	@Override
	public int available() throws IOException {
		return in.available();
	}

	@Override
	public void mark(int readlimit) {
		in.mark(readlimit);
	}

	@Override
	public boolean markSupported() {
		return in.markSupported();
	}

	@Override
	public void reset() throws IOException {
		in.reset();
	}

	@Override
	public long skip(long n) throws IOException {
		return in.skip(n);
	}

	boolean hasTimedOut() {
		synchronized (lock) {
			return readStartedMs != -1 &&
					clock.currentTimeMillis() - readStartedMs > timeoutMs;
		}
	}

	interface CloseListener {

		void onClose(TimeoutInputStream closed);
	}
}
