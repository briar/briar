package net.sf.briar.api.protocol;

import java.io.InputStream;

public interface ProtocolReaderFactory {

	ProtocolReader createProtocolReader(InputStream in);
}
