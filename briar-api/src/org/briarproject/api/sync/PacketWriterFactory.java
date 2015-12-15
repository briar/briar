package org.briarproject.api.sync;

import java.io.OutputStream;

public interface PacketWriterFactory {

	PacketWriter createPacketWriter(OutputStream out);
}
