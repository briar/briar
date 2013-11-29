package net.sf.briar.messaging.simplex;

import javax.inject.Singleton;

import net.sf.briar.api.messaging.simplex.SimplexConnectionFactory;

import com.google.inject.AbstractModule;

public class SimplexMessagingModule extends AbstractModule {

	protected void configure() {
		bind(SimplexConnectionFactory.class).to(
				SimplexConnectionFactoryImpl.class).in(Singleton.class);
	}
}
