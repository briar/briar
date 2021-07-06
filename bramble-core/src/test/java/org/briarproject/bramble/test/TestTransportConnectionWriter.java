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
	private final boolean lossyAndCheap;
	private final CountDownLatch disposed = new CountDownLatch(1);

	public TestTransportConnectionWriter(OutputStream out,
			boolean lossyAndCheap) {
		this.out = out;
		this.lossyAndCheap = lossyAndCheap;
	}

	public CountDownLatch getDisposedLatch() {
		return disposed;
	}

	@Override
	public long getMaxLatency() {
		return 30_000;
	}

	@Override
	public int getMaxIdleTime() {
		return 60_000;
	}

	@Override
	public boolean isLossyAndCheap() {
		return lossyAndCheap;
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
