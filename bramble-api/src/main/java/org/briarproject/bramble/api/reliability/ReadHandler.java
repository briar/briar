package org.briarproject.bramble.api.reliability;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.IOException;

@NotNullByDefault
public interface ReadHandler {

	void handleRead(byte[] b) throws IOException;
}
