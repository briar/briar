package net.sf.briar.plugins.modem;

import java.io.IOException;

interface WriteHandler {

	void handleWrite(byte[] b, int length) throws IOException;
}
