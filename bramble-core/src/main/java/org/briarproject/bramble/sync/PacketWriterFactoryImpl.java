package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.PacketWriter;
import org.briarproject.bramble.api.sync.PacketWriterFactory;

import java.io.OutputStream;

@NotNullByDefault
class PacketWriterFactoryImpl implements PacketWriterFactory {

	@Override
	public PacketWriter createPacketWriter(OutputStream out) {
		return new PacketWriterImpl(out);
	}
}
