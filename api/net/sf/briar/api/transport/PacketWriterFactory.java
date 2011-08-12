package net.sf.briar.api.transport;

import java.io.OutputStream;

public interface PacketWriterFactory {

	PacketWriter createPacketWriter(OutputStream out, int transportId,
			long connection, byte[] secret);
}
