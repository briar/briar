package net.sf.briar.transport.batch;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import net.sf.briar.api.transport.batch.BatchTransportWriter;

class TestBatchTransportWriter extends FilterOutputStream
implements BatchTransportWriter {

	private int capacity;

	TestBatchTransportWriter(OutputStream out, int capacity) {
		super(out);
		this.capacity = capacity;
	}

	public long getCapacity() throws IOException {
		return capacity;
	}

	public OutputStream getOutputStream() throws IOException {
		return this;
	}

	public void dispose() throws IOException {
		// Nothing to do
	}

	@Override
	public void write(int b) throws IOException {
		if(capacity < 1) throw new IllegalArgumentException();
		out.write(b);
		capacity--;
	}

	@Override
	public void write(byte[] b) throws IOException {
		write(b, 0, b.length);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		if(len > capacity) throw new IllegalArgumentException();
		out.write(b, off, len);
		capacity -= len;
	}
}
