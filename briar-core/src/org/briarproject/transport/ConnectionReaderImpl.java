package org.briarproject.transport;

import static org.briarproject.api.transport.TransportConstants.HEADER_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAC_LENGTH;

import java.io.IOException;
import java.io.InputStream;

import org.briarproject.api.transport.ConnectionReader;

class ConnectionReaderImpl extends InputStream implements ConnectionReader {

	private final FrameReader in;
	private final byte[] frame;

	private int offset = 0, length = 0;

	ConnectionReaderImpl(FrameReader in, int frameLength) {
		this.in = in;
		frame = new byte[frameLength - MAC_LENGTH];
	}

	public InputStream getInputStream() {
		return this;
	}

	@Override
	public int read() throws IOException {
		while(length <= 0) {
			if(length == -1) return -1;
			readFrame();
		}
		int b = frame[offset] & 0xff;
		offset++;
		length--;
		return b;
	}

	@Override
	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		while(length <= 0) {
			if(length == -1) return -1;
			readFrame();
		}
		len = Math.min(len, length);
		System.arraycopy(frame, offset, b, off, len);
		offset += len;
		length -= len;
		return len;
	}

	private void readFrame() throws IOException {
		assert length == 0;
		offset = HEADER_LENGTH;
		length = in.readFrame(frame);
	}
}
