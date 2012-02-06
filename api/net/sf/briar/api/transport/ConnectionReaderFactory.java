package net.sf.briar.api.transport;

import java.io.InputStream;

public interface ConnectionReaderFactory {

	/**
	 * Creates a connection reader for a simplex connection or one side of a
	 * duplex connection. The secret is erased before this method returns.
	 */
	ConnectionReader createConnectionReader(InputStream in, byte[] secret,
			boolean initiator);
}
