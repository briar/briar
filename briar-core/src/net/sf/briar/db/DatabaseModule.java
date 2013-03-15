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

	/** The minimum number of database threads to keep in the pool. */
	private static final int MIN_DB_THREADS = 1;

	/** The maximum number of database threads. */
	private static final int MAX_DB_THREADS = 10;

	/** The time in milliseconds to keep unused database threads alive. */
	private static final int DB_KEEPALIVE = 60 * 1000;

	@Override
	protected void configure() {
		bind(DatabaseCleaner.class).to(DatabaseCleanerImpl.class);
		// Database tasks may depend on each other, so use an unbounded queue
		BlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>();
		bind(Executor.class).annotatedWith(DatabaseExecutor.class).toInstance(
				new ThreadPoolExecutor(MIN_DB_THREADS, MAX_DB_THREADS,
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
