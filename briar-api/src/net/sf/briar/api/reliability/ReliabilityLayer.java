package net.sf.briar.api.reliability;

import java.io.InputStream;
import java.io.OutputStream;

public interface ReliabilityLayer extends ReadHandler {

	void start();

	void stop();

	InputStream getInputStream();

	OutputStream getOutputStream();
}
