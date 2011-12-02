package net.sf.briar.api.transport;

import java.io.InputStream;

public interface ConnectionReaderFactory {

	/**
	 * Creates a connection reader for a batch-mode connection or the
	 * initiator's side of a stream-mode connection.
	 */
	ConnectionReader createConnectionReader(InputStream in,
			ConnectionContext ctx, byte[] tag);

	/**
	 * Creates a connection reader for the responder's side of a stream-mode
	 * connection.
	 */
	ConnectionReader createConnectionReader(InputStream in,
			ConnectionContext ctx);
}
