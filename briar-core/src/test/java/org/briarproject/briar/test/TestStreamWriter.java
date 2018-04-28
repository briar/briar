package org.briarproject.briar.test;

import org.briarproject.bramble.api.transport.StreamWriter;

import java.io.IOException;
import java.io.OutputStream;

class TestStreamWriter implements StreamWriter {

	private final OutputStream out;

	TestStreamWriter(OutputStream out) {
		this.out = out;
	}

	@Override
	public OutputStream getOutputStream() {
		return out;
	}

	@Override
	public void sendEndOfStream() throws IOException {
		out.flush();
	}
}
