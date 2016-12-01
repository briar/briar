package org.briarproject.bramble.event;

import org.briarproject.bramble.api.event.EventBus;

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
