package net.sf.briar.transport.batch;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import net.sf.briar.api.transport.batch.BatchTransportReader;

class ByteArrayBatchTransportReader extends FilterInputStream
implements BatchTransportReader {

	ByteArrayBatchTransportReader(ByteArrayInputStream in) {
		super(in);
	}

	public InputStream getInputStream() throws IOException {
		return this;
	}

	public void dispose() throws IOException {
		// Nothing to do
	}

	@Override
	public int read() throws IOException {
		return in.read();
	}

	@Override
	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		return in.read(b, off, len);
	}
}
