package org.briarproject.bramble.api.transport;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An interface for writing data to a transport connection. Data will be
 * encrypted and authenticated before being written to the connection.
 */
public interface StreamWriter {

	OutputStream getOutputStream();

	/**
	 * Sends the end of stream marker, informing the recipient that no more
	 * data will be sent. The connection is flushed but not closed.
	 */
	void sendEndOfStream() throws IOException;
}
