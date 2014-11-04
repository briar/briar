package org.briarproject.messaging;

import static org.briarproject.api.transport.TransportConstants.MAX_FRAME_LENGTH;

import java.io.InputStream;

import org.briarproject.api.plugins.TransportConnectionReader;

class TestTransportConnectionReader implements TransportConnectionReader {

	private final InputStream in;

	private boolean disposed = false;

	TestTransportConnectionReader(InputStream in) {
		this.in = in;
	}

	public int getMaxFrameLength() {
		return MAX_FRAME_LENGTH;
	}

	public long getMaxLatency() {
		return Long.MAX_VALUE;
	}

	public InputStream getInputStream() {
		return in;
	}

	public void dispose(boolean exception, boolean recognised) {
		assert !disposed;
		disposed = true;
	}
}