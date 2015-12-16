package org.briarproject.api.sync;

import java.io.InputStream;

public interface PacketReaderFactory {

	PacketReader createPacketReader(InputStream in);
}
