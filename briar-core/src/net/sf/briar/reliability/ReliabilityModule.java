package net.sf.briar.reliability;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import net.sf.briar.api.reliability.ReliabilityExecutor;
import net.sf.briar.api.reliability.ReliabilityLayerFactory;

import com.google.inject.AbstractModule;

public class ReliabilityModule extends AbstractModule {

	@Override
	protected void configure() {
		// The executor is unbounded - tasks are expected to be long-lived
		bind(Executor.class).annotatedWith(
				ReliabilityExecutor.class).toInstance(
						Executors.newCachedThreadPool());
		bind(ReliabilityLayerFactory.class).to(
				ReliabilityLayerFactoryImpl.class);
	}
}
