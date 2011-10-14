package net.sf.briar.transport.batch;

import net.sf.briar.api.transport.batch.BatchConnectionFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class TransportBatchModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(BatchConnectionFactory.class).to(
				BatchConnectionFactoryImpl.class).in(Singleton.class);
	}
}
