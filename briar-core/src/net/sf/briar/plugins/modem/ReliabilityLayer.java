package net.sf.briar.plugins.modem;

import java.io.InputStream;
import java.io.OutputStream;

interface ReliabilityLayer extends ReadHandler, WriteHandler {

	void start();

	void stop();

	InputStream getInputStream();

	OutputStream getOutputStream();
}
