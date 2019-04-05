package org.briarproject.bramble.event;

import org.briarproject.bramble.api.event.EventExecutor;

import java.util.concurrent.Executor;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static java.util.concurrent.Executors.newSingleThreadExecutor;

/**
 * Default implementation of {@link EventExecutor} that uses a dedicated thread
 * to notify listeners of events. Applications may prefer to supply an
 * implementation that uses an existing thread, such as the UI thread.
 */
@Module
public class DefaultEventExecutorModule {

	@Provides
	@Singleton
	@EventExecutor
	Executor provideEventExecutor() {
		return newSingleThreadExecutor(r -> {
			Thread t = new Thread(r);
			t.setDaemon(true);
			return t;
		});
	}
}
