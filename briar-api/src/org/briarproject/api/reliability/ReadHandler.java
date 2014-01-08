package org.briarproject.api.reliability;

import java.io.IOException;

public interface ReadHandler {

	void handleRead(byte[] b) throws IOException;
}
