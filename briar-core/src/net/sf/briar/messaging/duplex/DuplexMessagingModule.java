package net.sf.briar.messaging.duplex;

import net.sf.briar.api.messaging.duplex.DuplexConnectionFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class DuplexMessagingModule extends AbstractModule {

	protected void configure() {
		bind(DuplexConnectionFactory.class).to(
				DuplexConnectionFactoryImpl.class).in(Singleton.class);
	}
}
