package net.sf.briar.messaging.duplex;

import javax.inject.Singleton;

import net.sf.briar.api.messaging.duplex.DuplexConnectionFactory;

import com.google.inject.AbstractModule;

public class DuplexMessagingModule extends AbstractModule {

	protected void configure() {
		bind(DuplexConnectionFactory.class).to(
				DuplexConnectionFactoryImpl.class).in(Singleton.class);
	}
}
