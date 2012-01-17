package net.sf.briar.transport;

import java.io.IOException;

import net.sf.briar.api.plugins.Segment;

interface IncomingEncryptionLayer {

	/**
	 * Reads a segment, excluding its tag, into the given buffer. Returns false
	 * if no more segments can be read from the connection.
	 */
	boolean readSegment(Segment s) throws IOException;
}
