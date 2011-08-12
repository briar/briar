package net.sf.briar.transport;

import net.sf.briar.api.transport.ConnectionWindowFactory;
import net.sf.briar.api.transport.PacketReaderFactory;
import net.sf.briar.api.transport.PacketWriterFactory;

import com.google.inject.AbstractModule;

public class TransportModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(ConnectionWindowFactory.class).to(
				ConnectionWindowFactoryImpl.class);
		bind(PacketReaderFactory.class).to(PacketReaderFactoryImpl.class);
		bind(PacketWriterFactory.class).to(PacketWriterFactoryImpl.class);
	}
}
