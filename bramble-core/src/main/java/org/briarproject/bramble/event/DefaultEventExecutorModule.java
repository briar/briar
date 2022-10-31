package org.briarproject.bramble.event;

import org.briarproject.bramble.api.event.EventExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

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
	Executor provideEventExecutor(ThreadFactory threadFactory) {
		return newSingleThreadExecutor(r -> {
			Thread t = threadFactory.newThread(r);
			t.setDaemon(true);
			t.setName(t.getName() + "-Event");
			return t;
		});
	}
}
