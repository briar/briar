package net.sf.briar.protocol.batch;

import net.sf.briar.api.protocol.batch.BatchConnectionFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class ProtocolBatchModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(BatchConnectionFactory.class).to(
				BatchConnectionFactoryImpl.class).in(Singleton.class);
	}
}
