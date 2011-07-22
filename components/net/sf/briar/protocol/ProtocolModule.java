package net.sf.briar.protocol;

import com.google.inject.AbstractModule;

public class ProtocolModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(BatchFactory.class).to(BatchFactoryImpl.class);
	}
}
