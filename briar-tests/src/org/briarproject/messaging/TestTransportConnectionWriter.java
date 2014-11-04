package org.briarproject.messaging;

import static org.briarproject.api.transport.TransportConstants.MAX_FRAME_LENGTH;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import org.briarproject.api.plugins.TransportConnectionWriter;

class TestTransportConnectionWriter implements TransportConnectionWriter {

	private final ByteArrayOutputStream out;

	private boolean disposed = false;

	TestTransportConnectionWriter(ByteArrayOutputStream out) {
		this.out = out;
	}

	public long getCapacity() {
		return Long.MAX_VALUE;
	}

	public int getMaxFrameLength() {
		return MAX_FRAME_LENGTH;
	}

	public long getMaxLatency() {
		return Long.MAX_VALUE;
	}

	public OutputStream getOutputStream() {
		return out;
	}

	public void dispose(boolean exception) {
		assert !disposed;
		disposed = true;
	}
}