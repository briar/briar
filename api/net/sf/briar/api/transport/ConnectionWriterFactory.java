package net.sf.briar.api.transport;

import java.io.OutputStream;

import net.sf.briar.api.TransportId;

public interface ConnectionWriterFactory {

	ConnectionWriter createConnectionWriter(OutputStream out, long capacity,
			boolean initiator, TransportId t, long connection, byte[] secret);

	ConnectionWriter createConnectionWriter(OutputStream out, long capacity,
			byte[] encryptedIv, byte[] secret);
}
