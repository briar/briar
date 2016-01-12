package org.briarproject.property;

import com.google.inject.AbstractModule;

import org.briarproject.api.property.TransportPropertyManager;

public class PropertyModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(TransportPropertyManager.class).to(
				TransportPropertyManagerImpl.class);
	}
}
