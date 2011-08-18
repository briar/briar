package net.sf.briar.api.transport;

import java.io.InputStream;

public interface ConnectionReaderFactory {

	ConnectionReader createConnectionReader(InputStream in, int transportId,
			long connection, byte[] secret);
}
