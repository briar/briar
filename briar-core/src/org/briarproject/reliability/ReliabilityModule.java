package org.briarproject.reliability;

import org.briarproject.api.reliability.ReliabilityLayerFactory;

import com.google.inject.AbstractModule;

public class ReliabilityModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(ReliabilityLayerFactory.class).to(
				ReliabilityLayerFactoryImpl.class);
	}
}
