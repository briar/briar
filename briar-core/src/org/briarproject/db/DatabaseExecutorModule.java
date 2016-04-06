package org.briarproject.db;

import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.lifecycle.LifecycleManager;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static java.util.concurrent.TimeUnit.SECONDS;

@Module
public class DatabaseExecutorModule {

	public static class EagerSingletons {
		@Inject
		@DatabaseExecutor
		ExecutorService executorService;
	}

	private final ExecutorService databaseExecutor;

	public DatabaseExecutorModule() {
		// Use an unbounded queue
		BlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>();
		// Discard tasks that are submitted during shutdown
		RejectedExecutionHandler policy =
				new ThreadPoolExecutor.DiscardPolicy();
		// Use a single thread and keep it in the pool for 60 secs
		databaseExecutor = new ThreadPoolExecutor(0, 1, 60, SECONDS, queue,
				policy);
	}

	@Provides
	@Singleton
	@DatabaseExecutor
	ExecutorService provideDatabaseExecutorService(
			LifecycleManager lifecycleManager) {
		lifecycleManager.registerForShutdown(databaseExecutor);
		return databaseExecutor;
	}

	@Provides
	@Singleton
	@DatabaseExecutor
	Executor provideDatabaseExecutor(
			@DatabaseExecutor ExecutorService dbExecutor) {
		return dbExecutor;
	}
}
