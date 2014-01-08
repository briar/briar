package org.briarproject.messaging.simplex;

import static org.briarproject.api.transport.TransportConstants.MAX_FRAME_LENGTH;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import org.briarproject.api.plugins.simplex.SimplexTransportWriter;

class TestSimplexTransportWriter implements SimplexTransportWriter {

	private final ByteArrayOutputStream out;
	private final long capacity, maxLatency;
	private final boolean flush;

	private boolean disposed = false, exception = false;

	TestSimplexTransportWriter(ByteArrayOutputStream out, long capacity,
			long maxLatency, boolean flush) {
		this.out = out;
		this.capacity = capacity;
		this.maxLatency = maxLatency;
		this.flush = flush;
	}

	public long getCapacity() {
		return capacity;
	}

	public int getMaxFrameLength() {
		return MAX_FRAME_LENGTH;
	}

	public long getMaxLatency() {
		return maxLatency;
	}

	public OutputStream getOutputStream() {
		return out;
	}

	public boolean shouldFlush() {
		return flush;
	}

	public void dispose(boolean exception) {
		assert !disposed;
		disposed = true;
		this.exception = exception;
	}

	boolean getDisposed() {
		return disposed;
	}

	boolean getException() {
		return exception;
	}
}