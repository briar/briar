package net.sf.briar.reliability;

import java.io.IOException;
import java.io.InputStream;

class ReceiverInputStream extends InputStream {

	private final Receiver receiver;

	private Data data = null;
	private int offset = 0, length = 0;

	ReceiverInputStream(Receiver receiver) {
		this.receiver = receiver;
	}

	@Override
	public int read() throws IOException {
		if(length == -1) return -1;
		while(length == 0) if(!receive()) return -1;
		int b = data.getBuffer()[offset] & 0xff;
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
		if(length == -1) return -1;
		while(length == 0) if(!receive()) return -1;
		len = Math.min(len, length);
		System.arraycopy(data.getBuffer(), offset, b, off, len);
		offset += len;
		length -= len;
		return len;
	}

	private boolean receive() throws IOException {
		assert length == 0;
		if(data != null && data.isLastFrame()) {
			length = -1;
			return false;
		}
		try {
			data = receiver.read();
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while reading");
		}
		offset = Data.HEADER_LENGTH;
		length = data.getLength() - Data.MIN_LENGTH;
		return true;
	}
}
