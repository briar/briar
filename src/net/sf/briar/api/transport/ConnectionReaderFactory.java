package net.sf.briar.api.transport;

import java.io.InputStream;

public interface ConnectionReaderFactory {

	/**
	 * Creates a connection reader for one side of a connection.
	 */
	ConnectionReader createConnectionReader(InputStream in,
			ConnectionContext ctx, boolean incoming, boolean initiator);
}
