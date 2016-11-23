package org.briarproject.bramble.api.sync;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.OutputStream;

@NotNullByDefault
public interface PacketWriterFactory {

	PacketWriter createPacketWriter(OutputStream out);
}
