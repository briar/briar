package org.briarproject.sync;

import org.briarproject.api.data.WriterFactory;
import org.briarproject.api.sync.PacketWriter;
import org.briarproject.api.sync.PacketWriterFactory;

import java.io.OutputStream;

import javax.inject.Inject;

class PacketWriterFactoryImpl implements PacketWriterFactory {

	private final WriterFactory writerFactory;

	@Inject
	PacketWriterFactoryImpl(WriterFactory writerFactory) {
		this.writerFactory = writerFactory;
	}

	public PacketWriter createPacketWriter(OutputStream out) {
		return new PacketWriterImpl(writerFactory, out);
	}
}
