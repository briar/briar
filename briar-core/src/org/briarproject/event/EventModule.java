package org.briarproject.event;

import org.briarproject.api.event.EventBus;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class EventModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(EventBus.class).to(EventBusImpl.class).in(Singleton.class);
	}
}
