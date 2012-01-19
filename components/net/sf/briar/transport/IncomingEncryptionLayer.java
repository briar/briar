package net.sf.briar.transport;

import java.io.IOException;

import net.sf.briar.api.transport.Segment;

interface IncomingEncryptionLayer {

	/**
	 * Reads a segment, excluding its tag, into the given buffer. Returns false
	 * if no more segments can be read from the connection.
	 * @throws IOException if an unrecoverable error occurs and the connection
	 * must be closed.
	 * @throws InvalidDataException if a recoverable error occurs. The caller
	 * may choose whether to retry the read or close the connection.
	 */
	boolean readSegment(Segment s) throws IOException, InvalidDataException;
}
