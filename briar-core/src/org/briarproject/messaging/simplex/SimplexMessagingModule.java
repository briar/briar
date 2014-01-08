package org.briarproject.messaging.simplex;

import javax.inject.Singleton;

import org.briarproject.api.messaging.simplex.SimplexConnectionFactory;

import com.google.inject.AbstractModule;

public class SimplexMessagingModule extends AbstractModule {

	protected void configure() {
		bind(SimplexConnectionFactory.class).to(
				SimplexConnectionFactoryImpl.class).in(Singleton.class);
	}
}
