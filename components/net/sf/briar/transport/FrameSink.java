package net.sf.briar.transport;

import java.io.IOException;

interface FrameSink {

	/** Writes the given frame. */
	void writeFrame(byte[] b, int len) throws IOException;
}
