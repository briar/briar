package net.sf.briar.protocol;

import net.sf.briar.api.protocol.GroupFactory;

import com.google.inject.AbstractModule;

public class ProtocolModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(AckFactory.class).to(AckFactoryImpl.class);
		bind(BatchFactory.class).to(BatchFactoryImpl.class);
		bind(GroupFactory.class).to(GroupFactoryImpl.class);
	}
}
