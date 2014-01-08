package org.briarproject.messaging.simplex;

import static org.briarproject.api.transport.TransportConstants.MAX_FRAME_LENGTH;

import java.io.InputStream;

import org.briarproject.api.plugins.simplex.SimplexTransportReader;

class TestSimplexTransportReader implements SimplexTransportReader {

	private final InputStream in;

	private boolean disposed = false, exception = false, recognised = false;

	TestSimplexTransportReader(InputStream in) {
		this.in = in;
	}

	public int getMaxFrameLength() {
		return MAX_FRAME_LENGTH;
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