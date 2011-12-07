package net.sf.briar.protocol;

import java.io.OutputStream;

import net.sf.briar.api.protocol.ProtocolWriter;
import net.sf.briar.api.protocol.ProtocolWriterFactory;
import net.sf.briar.api.serial.SerialComponent;
import net.sf.briar.api.serial.WriterFactory;

import com.google.inject.Inject;

class ProtocolWriterFactoryImpl implements ProtocolWriterFactory {

	private final SerialComponent serial;
	private final WriterFactory writerFactory;

	@Inject
	ProtocolWriterFactoryImpl(SerialComponent serial,
			WriterFactory writerFactory) {
		this.serial = serial;
		this.writerFactory = writerFactory;
	}

	public ProtocolWriter createProtocolWriter(OutputStream out) {
		return new ProtocolWriterImpl(serial, writerFactory, out);
	}
}
