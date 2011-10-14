package net.sf.briar.transport.stream;

import net.sf.briar.api.transport.StreamConnectionFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class TransportStreamModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(StreamConnectionFactory.class).to(
				StreamConnectionFactoryImpl.class).in(Singleton.class);
	}
}
