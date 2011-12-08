package net.sf.briar.protocol.stream;

import net.sf.briar.api.protocol.stream.StreamConnectionFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class ProtocolStreamModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(StreamConnectionFactory.class).to(
				StreamConnectionFactoryImpl.class).in(Singleton.class);
	}
}
