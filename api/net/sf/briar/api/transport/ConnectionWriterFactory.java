package net.sf.briar.api.transport;

import java.io.OutputStream;

public interface ConnectionWriterFactory {

	ConnectionWriter createConnectionWriter(OutputStream out, boolean initiator,
			int transportId, long connection, byte[] secret);
}
