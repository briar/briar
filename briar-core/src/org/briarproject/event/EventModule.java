package org.briarproject.event;

import org.briarproject.api.event.EventBus;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class EventModule {

	@Provides
	@Singleton
	EventBus provideEventBus() {
		return new EventBusImpl();
	}
}
