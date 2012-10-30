package net.sf.briar.protocol.simplex;

import net.sf.briar.api.protocol.simplex.SimplexConnectionFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class SimplexProtocolModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(SimplexConnectionFactory.class).to(
				SimplexConnectionFactoryImpl.class).in(Singleton.class);
	}
}
