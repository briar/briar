package net.sf.briar.api.plugins;

import java.io.IOException;

public interface FrameSource {

	/**
	 * Reads a frame into the given buffer and returns its length, or -1 if no
	 * more frames can be read.
	 */
	int readFrame(byte[] b) throws IOException;
}
