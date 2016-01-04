package org.briarproject.sync;

import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.sync.PacketWriter;
import org.briarproject.api.sync.PacketWriterFactory;

import java.io.OutputStream;

import javax.inject.Inject;

class PacketWriterFactoryImpl implements PacketWriterFactory {

	private final BdfWriterFactory bdfWriterFactory;

	@Inject
	PacketWriterFactoryImpl(BdfWriterFactory bdfWriterFactory) {
		this.bdfWriterFactory = bdfWriterFactory;
	}

	public PacketWriter createPacketWriter(OutputStream out) {
		return new PacketWriterImpl(bdfWriterFactory, out);
	}
}
