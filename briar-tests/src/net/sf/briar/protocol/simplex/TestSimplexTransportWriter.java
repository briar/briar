package net.sf.briar.protocol.simplex;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import net.sf.briar.api.plugins.simplex.SimplexTransportWriter;

class TestSimplexTransportWriter implements SimplexTransportWriter {

	private final ByteArrayOutputStream out;
	private final long capacity;
	private final boolean flush;

	private boolean disposed = false, exception = false;

	TestSimplexTransportWriter(ByteArrayOutputStream out, long capacity,
			boolean flush) {
		this.out = out;
		this.capacity = capacity;
		this.flush = flush;
	}

	public long getCapacity() {
		return capacity;
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