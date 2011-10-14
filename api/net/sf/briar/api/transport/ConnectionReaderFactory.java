package net.sf.briar.api.transport;

import java.io.InputStream;

import net.sf.briar.api.TransportId;

public interface ConnectionReaderFactory {

	ConnectionReader createConnectionReader(InputStream in, byte[] encryptedIv,
			byte[] secret);

	ConnectionReader createConnectionReader(InputStream in, boolean initiator,
			TransportId t, long connection, byte[] secret);
}
