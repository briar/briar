package org.briarproject.messaging;

import java.io.OutputStream;

import javax.inject.Inject;

import org.briarproject.api.messaging.PacketWriter;
import org.briarproject.api.messaging.PacketWriterFactory;
import org.briarproject.api.serial.SerialComponent;
import org.briarproject.api.serial.WriterFactory;

class PacketWriterFactoryImpl implements PacketWriterFactory {

	private final SerialComponent serial;
	private final WriterFactory writerFactory;

	@Inject
	PacketWriterFactoryImpl(SerialComponent serial,
			WriterFactory writerFactory) {
		this.serial = serial;
		this.writerFactory = writerFactory;
	}

	public PacketWriter createPacketWriter(OutputStream out,
			boolean flush) {
		return new PacketWriterImpl(serial, writerFactory, out, flush);
	}
}
