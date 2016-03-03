package org.briarproject.reliability;

import org.briarproject.api.lifecycle.IoExecutor;
import org.briarproject.api.reliability.ReliabilityLayerFactory;

import java.util.concurrent.Executor;

import dagger.Module;
import dagger.Provides;

@Module
public class ReliabilityModule {

	/*
	@Provides
	ReliabilityLayerFactory provideReliabilityFactory(@IoExecutor
	Executor ioExecutor) {
		return new ReliabilityLayerFactoryImpl(ioExecutor);
	}
	*/



	@Provides
	ReliabilityLayerFactory provideReliabilityFactory(
			ReliabilityLayerFactoryImpl reliabilityLayerFactory) {
		return reliabilityLayerFactory;
	}

}
