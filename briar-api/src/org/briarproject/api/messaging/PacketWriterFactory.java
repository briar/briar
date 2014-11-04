package org.briarproject.api.messaging;

import java.io.OutputStream;

public interface PacketWriterFactory {

	PacketWriter createPacketWriter(OutputStream out);
}
