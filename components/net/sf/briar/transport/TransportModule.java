package net.sf.briar.transport;

import net.sf.briar.api.transport.ConnectionWindowFactory;

import com.google.inject.AbstractModule;

public class TransportModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(ConnectionWindowFactory.class).to(
				ConnectionWindowFactoryImpl.class);
	}
}
