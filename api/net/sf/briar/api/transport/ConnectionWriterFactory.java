package net.sf.briar.api.transport;

import java.io.OutputStream;

import net.sf.briar.api.TransportId;

public interface ConnectionWriterFactory {

	/**
	 * Creates a connection writer for a batch-mode connection or the
	 * initiator's side of a stream-mode connection.
	 */
	ConnectionWriter createConnectionWriter(OutputStream out, long capacity,
			TransportId t, long connection, byte[] secret);

	/**
	 * Creates a connection writer for the responder's side of a stream-mode
	 * connection.
	 */
	ConnectionWriter createConnectionWriter(OutputStream out, long capacity,
			TransportId t, byte[] encryptedIv, byte[] secret);
}
