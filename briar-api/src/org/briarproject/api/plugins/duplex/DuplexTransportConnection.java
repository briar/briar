package org.briarproject.api.plugins.duplex;

import org.briarproject.api.plugins.TransportConnectionReader;
import org.briarproject.api.plugins.TransportConnectionWriter;

/**
 * An interface for reading and writing data over a duplex transport. The
 * connection is not responsible for encrypting/decrypting or authenticating
 * the data.
 */
public interface DuplexTransportConnection {

	/** Returns a {@link org.briarproject.api.plugins.TransportConnectionReader
	 * TransportConnectionReader} for reading from the connection. */
	TransportConnectionReader getReader();

	/** Returns a {@link org.briarproject.api.plugins.TransportConnectionWriter
	 * TransportConnectionWriter} for writing to the connection. */
	TransportConnectionWriter getWriter();
}
