package net.sf.briar.protocol.batch;

import java.io.InputStream;

import net.sf.briar.api.transport.BatchTransportReader;

class TestBatchTransportReader
implements BatchTransportReader {

	private final InputStream in;

	private boolean success = false;

	TestBatchTransportReader(InputStream in) {
		this.in = in;
	}

	public InputStream getInputStream() {
		return in;
	}

	public void dispose(boolean success) {
		this.success = success;
	}

	boolean getSuccess() {
		return success;
	}
}