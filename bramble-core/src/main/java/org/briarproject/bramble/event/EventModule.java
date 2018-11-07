package org.briarproject.bramble.event;

import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventExecutor;

import java.util.concurrent.Executor;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class EventModule {

	@Provides
	@Singleton
	EventBus provideEventBus(@EventExecutor Executor eventExecutor) {
		return new EventBusImpl(eventExecutor);
	}
}
