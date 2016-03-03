package org.briarproject.lifecycle;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;

import javax.inject.Singleton;

import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.lifecycle.IoExecutor;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.lifecycle.ShutdownManager;
import org.briarproject.api.system.Clock;

import dagger.Module;
import dagger.Provides;

@Module
public class LifecycleModule {

	private final ExecutorService ioExecutor;

	public LifecycleModule() {
		// The thread pool is unbounded, so use direct handoff
		BlockingQueue<Runnable> queue = new SynchronousQueue<Runnable>();
		// Discard tasks that are submitted during shutdown
		RejectedExecutionHandler policy =
				new ThreadPoolExecutor.DiscardPolicy();
		// Create threads as required and keep them in the pool for 60 seconds
		ioExecutor = new ThreadPoolExecutor(0, Integer.MAX_VALUE,
				60, SECONDS, queue, policy);
	}

	@Provides
	@Singleton
	ShutdownManager provideShutdownManager() {
		return new ShutdownManagerImpl();
	}

	@Provides
	@Singleton
	LifecycleManager provideLifeCycleManager(Clock clock, DatabaseComponent db,
			EventBus eventBus) {
		return new LifecycleManagerImpl(clock, db, eventBus);
	}

	@Provides
	@Singleton
	@IoExecutor
	Executor getIoExecutor(LifecycleManager lifecycleManager) {
		lifecycleManager.registerForShutdown(ioExecutor);
		return ioExecutor;
	}

}
