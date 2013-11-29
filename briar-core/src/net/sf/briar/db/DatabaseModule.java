package net.sf.briar.db;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.sql.Connection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

import javax.inject.Singleton;

import net.sf.briar.api.clock.Clock;
import net.sf.briar.api.clock.SystemClock;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseConfig;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.lifecycle.LifecycleManager;
import net.sf.briar.api.lifecycle.ShutdownManager;
import net.sf.briar.api.os.FileUtils;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class DatabaseModule extends AbstractModule {

	/** The maximum number of executor threads. */
	private static final int MAX_EXECUTOR_THREADS = 10;

	private final ExecutorService databaseExecutor;

	public DatabaseModule() {
		// The queue is unbounded, so tasks can be dependent
		BlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>();
		// Discard tasks that are submitted during shutdown
		RejectedExecutionHandler policy =
				new ThreadPoolExecutor.DiscardPolicy();
		// Create a limited # of threads and keep them in the pool for 60 secs
		databaseExecutor = new ThreadPoolExecutor(0, MAX_EXECUTOR_THREADS,
				60, SECONDS, queue, policy);
	}

	protected void configure() {
		bind(DatabaseCleaner.class).to(DatabaseCleanerImpl.class);
	}

	@Provides
	Database<Connection> getDatabase(DatabaseConfig config,
			FileUtils fileUtils) {
		return new H2Database(config, fileUtils, new SystemClock());
	}

	@Provides @Singleton
	DatabaseComponent getDatabaseComponent(Database<Connection> db,
			DatabaseCleaner cleaner, ShutdownManager shutdown, Clock clock) {
		return new DatabaseComponentImpl<Connection>(db, cleaner, shutdown,
				clock);
	}

	@Provides @Singleton @DatabaseExecutor
	Executor getDatabaseExecutor(LifecycleManager lifecycleManager) {
		lifecycleManager.registerForShutdown(databaseExecutor);
		return databaseExecutor;
	}
}
