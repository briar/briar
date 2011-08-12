package net.sf.briar.protocol.writers;

import net.sf.briar.api.protocol.writers.ProtocolWriterFactory;

import com.google.inject.AbstractModule;

public class WritersModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(ProtocolWriterFactory.class).to(ProtocolWriterFactoryImpl.class);
	}
}
