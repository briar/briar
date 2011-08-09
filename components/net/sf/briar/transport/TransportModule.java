package net.sf.briar.transport;

import net.sf.briar.api.transport.PacketWriter;

import com.google.inject.AbstractModule;

public class TransportModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(PacketWriter.class).to(PacketWriterImpl.class);
	}
}
