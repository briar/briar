package net.sf.briar.transport.batch;

import java.io.IOException;
import java.io.InputStream;

import net.sf.briar.api.transport.batch.BatchTransportReader;

class TestBatchTransportReader implements BatchTransportReader {

	private final InputStream in;

	TestBatchTransportReader(InputStream in) {
		this.in = in;
	}

	public InputStream getInputStream() {
		return in;
	}

	public void dispose() throws IOException {
		// The input stream may have been left open
		in.close();
	}
}
