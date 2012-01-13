package net.sf.briar.api.plugins;

import java.io.IOException;

public interface FrameSink {

	/** Writes the given frame. */
	void writeFrame(byte[] b, int len) throws IOException;
}
