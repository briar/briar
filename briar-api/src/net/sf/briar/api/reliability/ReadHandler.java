package net.sf.briar.api.reliability;

import java.io.IOException;

public interface ReadHandler {

	void handleRead(byte[] b) throws IOException;
}
