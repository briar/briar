package net.sf.briar.protocol.simplex;

import java.io.InputStream;

import net.sf.briar.api.plugins.SimplexTransportReader;

class TestSimplexTransportReader implements SimplexTransportReader {

	private final InputStream in;

	private boolean disposed = false, exception = false, recognised = false;

	TestSimplexTransportReader(InputStream in) {
		this.in = in;
	}

	public InputStream getInputStream() {
		return in;
	}

	public void dispose(boolean exception, boolean recognised) {
		assert !disposed;
		disposed = true;
		this.exception = exception;
		this.recognised = recognised;
	}

	boolean getDisposed() {
		return disposed;
	}

	boolean getException() {
		return exception;
	}

	boolean getRecognised() {
		return recognised;
	}
}