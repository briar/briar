package org.briarproject.bramble.test;

import org.briarproject.bramble.api.transport.StreamWriter;

import java.io.IOException;
import java.io.OutputStream;

public class TestStreamWriter implements StreamWriter {

	private final OutputStream out;

	public TestStreamWriter(OutputStream out) {
		this.out = out;
	}

	@Override
	public OutputStream getOutputStream() {
		return out;
	}

	@Override
	public void sendEndOfStream() throws IOException {
		out.flush();
		out.close();
	}
}
