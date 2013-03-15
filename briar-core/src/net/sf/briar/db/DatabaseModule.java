package net.sf.briar.db;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.sql.Connection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import net.sf.briar.api.clock.Clock;
import net.sf.briar.api.clock.SystemClock;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseConfig;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.lifecycle.ShutdownManager;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

public class DatabaseModule extends AbstractModule {

	/**
	 * The maximum number of database threads. When a task is submitted to the
	 * database executor and no thread is available to run it, the task will be
	 * queued.
	 */
	private static final int MAX_DB_THREADS = 10;

	/** How many milliseconds to keep idle threads alive. */
	private static final int DB_KEEPALIVE = 60 * 1000;

	@Override
	protected void configure() {
		bind(DatabaseCleaner.class).to(DatabaseCleanerImpl.class);
		// Use an unbounded queue to prevent deadlock between submitted tasks
		BlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>();
		bind(Executor.class).annotatedWith(DatabaseExecutor.class).toInstance(
				new ThreadPoolExecutor(MAX_DB_THREADS, MAX_DB_THREADS,
						DB_KEEPALIVE, MILLISECONDS, queue));
	}

	@Provides
	Database<Connection> getDatabase(DatabaseConfig config) {
		return new H2Database(config, new SystemClock());
	}

	@Provides @Singleton
	DatabaseComponent getDatabaseComponent(Database<Connection> db,
			DatabaseCleaner cleaner, ShutdownManager shutdown, Clock clock) {
		return new DatabaseComponentImpl<Connection>(db, cleaner, shutdown,
				clock);
	}
}
