package net.sf.briar.protocol.writers;


import net.sf.briar.api.protocol.writers.PacketWriterFactory;

import com.google.inject.AbstractModule;

public class WritersModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(PacketWriterFactory.class).to(PacketWriterFactoryImpl.class);
	}
}
