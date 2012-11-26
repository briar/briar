package net.sf.briar.plugins.modem;

import java.io.IOException;

interface ReadHandler {

	void handleRead(byte[] b, int length) throws IOException;
}
