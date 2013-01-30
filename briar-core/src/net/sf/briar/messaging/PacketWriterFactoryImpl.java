package net.sf.briar.messaging;

import java.io.OutputStream;

import net.sf.briar.api.messaging.PacketWriter;
import net.sf.briar.api.messaging.PacketWriterFactory;
import net.sf.briar.api.serial.SerialComponent;
import net.sf.briar.api.serial.WriterFactory;

import com.google.inject.Inject;

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
