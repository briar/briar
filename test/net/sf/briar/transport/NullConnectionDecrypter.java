package net.sf.briar.transport;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/** A ConnectionDecrypter that performs no decryption. */
class NullConnectionDecrypter implements ConnectionDecrypter {

	private final InputStream in;

	NullConnectionDecrypter(InputStream in) {
		this.in = in;
	}

	public InputStream getInputStream() {
		return in;
	}

	public void readMac(byte[] mac) throws IOException {
		int offset = 0;
		while(offset < mac.length) {
			int read = in.read(mac, offset, mac.length - offset);
			if(read == -1) break;
			offset += read;
		}
		if(offset < mac.length) throw new EOFException();
	}
}
