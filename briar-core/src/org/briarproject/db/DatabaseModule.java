package org.briarproject.db;

import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DatabaseConfig;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.lifecycle.ShutdownManager;
import org.briarproject.api.system.Clock;

import java.security.SecureRandom;
import java.sql.Connection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static java.util.concurrent.TimeUnit.SECONDS;

@Module
public class DatabaseModule {

	private final ExecutorService databaseExecutor;

	public DatabaseModule() {
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
	Database<Connection> provideDatabase(DatabaseConfig config,
			SecureRandom random, Clock clock) {
		return new H2Database(config, random, clock);
	}

	@Provides @Singleton
	DatabaseComponent provideDatabaseComponent(Database<Connection> db,
			EventBus eventBus, ShutdownManager shutdown) {
		return new DatabaseComponentImpl<Connection>(db, Connection.class,
				eventBus, shutdown);
	}

	@Provides @Singleton @DatabaseExecutor
	ExecutorService provideDatabaseExecutorService(LifecycleManager lifecycleManager) {
		lifecycleManager.registerForShutdown(databaseExecutor);
		return databaseExecutor;
	}

	@Provides @Singleton @DatabaseExecutor
	Executor provideDatabaseExecutor(@DatabaseExecutor ExecutorService dbExecutor) {
		return dbExecutor;
	}
}
