package net.sf.briar.transport;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** A ConnectionEncrypter that performs no encryption. */
class NullConnectionEncrypter extends FilterOutputStream
implements ConnectionEncrypter {

	private long capacity;

	NullConnectionEncrypter(OutputStream out) {
		this(out, Long.MAX_VALUE);
	}

	NullConnectionEncrypter(OutputStream out, long capacity) {
		super(out);
		this.capacity = capacity;
	}

	public OutputStream getOutputStream() {
		return this;
	}

	public void writeMac(byte[] mac) throws IOException {
		out.write(mac);
		capacity -= mac.length;
	}

	public long getCapacity() {
		return capacity;
	}

	@Override
	public void write(int b) throws IOException {
		out.write(b);
		capacity--;
	}

	@Override
	public void write(byte[] b) throws IOException {
		write(b, 0, b.length);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		out.write(b, off, len);
		capacity -= len;
	}
}
