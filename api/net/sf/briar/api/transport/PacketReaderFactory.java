package net.sf.briar.api.transport;

import java.io.InputStream;

public interface PacketReaderFactory {

	PacketReader createPacketReader(byte[] firstTag, InputStream in,
			int transportId, long connection, byte[] secret);
}
