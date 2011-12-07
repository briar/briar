package net.sf.briar.api.protocol;

import java.io.OutputStream;

public interface ProtocolWriterFactory {

	ProtocolWriter createProtocolWriter(OutputStream out);
}
