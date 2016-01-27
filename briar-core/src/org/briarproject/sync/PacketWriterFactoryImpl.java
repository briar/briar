package org.briarproject.sync;

import org.briarproject.api.sync.PacketWriter;
import org.briarproject.api.sync.PacketWriterFactory;

import java.io.OutputStream;

class PacketWriterFactoryImpl implements PacketWriterFactory {

	public PacketWriter createPacketWriter(OutputStream out) {
		return new PacketWriterImpl(out);
	}
}
