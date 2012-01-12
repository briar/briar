package net.sf.briar.transport;

import java.io.IOException;
import java.io.OutputStream;

/** A ConnectionEncrypter that performs no encryption. */
class NullConnectionEncrypter implements ConnectionEncrypter {

	private final OutputStream out;

	private long capacity;

	NullConnectionEncrypter(OutputStream out) {
		this.out = out;
		capacity = Long.MAX_VALUE;
	}

	NullConnectionEncrypter(OutputStream out, long capacity) {
		this.out = out;
		this.capacity = capacity;
	}

	public void writeFrame(byte[] b, int len) throws IOException {
		out.write(b, 0, len);
		capacity -= len;
	}

	public void flush() throws IOException {
		out.flush();
	}

	public long getRemainingCapacity() {
		return capacity;
	}
}
