package net.sf.briar.api.serial;

import java.io.IOException;

public interface Consumer {

	void write(byte b) throws IOException;

	void write(byte[] b) throws IOException;

	void write(byte[] b, int off, int len) throws IOException;
}
