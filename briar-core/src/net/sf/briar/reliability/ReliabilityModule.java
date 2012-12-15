package net.sf.briar.reliability;

import net.sf.briar.api.reliability.ReliabilityLayerFactory;

import com.google.inject.AbstractModule;

public class ReliabilityModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(ReliabilityLayerFactory.class).to(
				ReliabilityLayerFactoryImpl.class);
	}
}
