package net.sf.briar.protocol.duplex;

import net.sf.briar.api.protocol.duplex.DuplexConnectionFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class DuplexProtocolModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(DuplexConnectionFactory.class).to(
				DuplexConnectionFactoryImpl.class).in(Singleton.class);
	}
}
