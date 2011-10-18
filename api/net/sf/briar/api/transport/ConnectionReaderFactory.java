package net.sf.briar.api.transport;

import java.io.InputStream;

import net.sf.briar.api.TransportId;

public interface ConnectionReaderFactory {

	/**
	 * Creates a connection reader for a batch-mode connection or the
	 * initiator's side of a stream-mode connection.
	 */
	ConnectionReader createConnectionReader(InputStream in, TransportId t,
			byte[] encryptedIv, byte[] secret);

	/**
	 * Creates a connection reader for the responder's side of a stream-mode
	 * connection.
	 */
	ConnectionReader createConnectionReader(InputStream in, TransportId t,
			long connection, byte[] secret);
}
