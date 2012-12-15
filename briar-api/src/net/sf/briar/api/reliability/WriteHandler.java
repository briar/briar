package net.sf.briar.api.reliability;

import java.io.IOException;

public interface WriteHandler {

	void handleWrite(byte[] b) throws IOException;
}
