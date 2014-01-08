package org.briarproject.messaging.duplex;

import javax.inject.Singleton;

import org.briarproject.api.messaging.duplex.DuplexConnectionFactory;

import com.google.inject.AbstractModule;

public class DuplexMessagingModule extends AbstractModule {

	protected void configure() {
		bind(DuplexConnectionFactory.class).to(
				DuplexConnectionFactoryImpl.class).in(Singleton.class);
	}
}
