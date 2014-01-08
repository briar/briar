package org.briarproject.api.reliability;

import java.io.IOException;

public interface WriteHandler {

	void handleWrite(byte[] b) throws IOException;
}
