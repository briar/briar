package org.briarproject.api.messaging;

import java.io.InputStream;

public interface PacketReaderFactory {

	PacketReader createPacketReader(InputStream in);
}
