package net.sf.briar.api.messaging;

import java.io.OutputStream;

public interface PacketWriterFactory {

	PacketWriter createPacketWriter(OutputStream out, boolean flush);
}
