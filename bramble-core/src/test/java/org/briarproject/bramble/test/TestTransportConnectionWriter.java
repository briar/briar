package org.briarproject.bramble.test;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportConnectionWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
@NotNullByDefault
public class TestTransportConnectionWriter
		implements TransportConnectionWriter {

	private final OutputStream out;
	private final CountDownLatch disposed = new CountDownLatch(1);

	public TestTransportConnectionWriter(OutputStream out) {
		this.out = out;
	}

	public CountDownLatch getDisposedLatch() {
		return disposed;
	}

	@Override
	public int getMaxLatency() {
		return 30_000;
	}

	@Override
	public int getMaxIdleTime() {
		return 60_000;
	}

	@Override
	public boolean isLossyAndCheap() {
		return false;
	}

	@Override
	public OutputStream getOutputStream() {
		return out;
	}

	@Override
	public void dispose(boolean exception) throws IOException {
		disposed.countDown();
		out.close();
	}
}
