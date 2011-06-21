package net.sf.briar.setup;

import net.sf.briar.api.setup.SetupWorkerFactory;

import com.google.inject.AbstractModule;

public class SetupModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(SetupWorkerFactory.class).to(SetupWorkerFactoryImpl.class);
	}
}
