package net.sf.briar.transport;

import java.io.IOException;
import java.io.OutputStream;

/** A ConnectionEncrypter that performs no encryption. */
class NullConnectionEncrypter implements ConnectionEncrypter {

	private final OutputStream out;

	NullConnectionEncrypter(OutputStream out) {
		this.out = out;
	}

	public OutputStream getOutputStream() {
		return out;
	}

	public void writeMac(byte[] mac) throws IOException {
		out.write(mac);
	}
}
