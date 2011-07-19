package net.sf.briar.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import net.sf.briar.api.serial.Consumer;

public class CopyingConsumer implements Consumer {

	private final ByteArrayOutputStream out = new ByteArrayOutputStream();

	byte[] getCopy() {
		return out.toByteArray();
	}

	public void write(byte b) throws IOException {
		out.write(b);
	}

	public void write(byte[] b) throws IOException {
		out.write(b);
	}

	public void write(byte[] b, int off, int len) throws IOException {
		out.write(b, off, len);
	}
}
