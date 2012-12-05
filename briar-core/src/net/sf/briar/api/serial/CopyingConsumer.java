package net.sf.briar.api.serial;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** A consumer that makes a copy of the bytes consumed. */
public class CopyingConsumer implements Consumer {

	private final ByteArrayOutputStream out = new ByteArrayOutputStream();

	public byte[] getCopy() {
		return out.toByteArray();
	}

	public void write(byte b) throws IOException {
		out.write(b);
	}

	public void write(byte[] b, int off, int len) throws IOException {
		out.write(b, off, len);
	}
}
