package net.sf.briar.transport.batch;

import java.io.IOException;
import java.io.OutputStream;

import net.sf.briar.api.transport.batch.BatchTransportWriter;

class TestBatchTransportWriter implements BatchTransportWriter {

	private final OutputStream out;
	private final int capacity;

	TestBatchTransportWriter(OutputStream out, int capacity) {
		this.out = out;
		this.capacity = capacity;
	}

	public long getCapacity() {
		return capacity;
	}

	public OutputStream getOutputStream() {
		return out;
	}

	public void dispose() throws IOException {
		// The output stream may have been left open
		out.close();
	}
}
