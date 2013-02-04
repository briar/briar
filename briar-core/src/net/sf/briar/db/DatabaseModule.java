package net.sf.briar.db;

import java.sql.Connection;
import java.util.concurrent.Executor;

import net.sf.briar.api.clock.Clock;
import net.sf.briar.api.clock.SystemClock;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseConfig;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.lifecycle.ShutdownManager;
import net.sf.briar.util.BoundedExecutor;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

public class DatabaseModule extends AbstractModule {

	// FIXME: Determine suitable values for these constants empirically

	/**
	 * The maximum number of database tasks that can be queued for execution
	 * before submitting another task will block.
	 */
	private static final int MAX_QUEUED_DB_TASKS = 10;

	/** The minimum number of database threads to keep in the pool. */
	private static final int MIN_DB_THREADS = 1;

	/** The maximum number of database threads. */
	private static final int MAX_DB_THREADS = 10;

	@Override
	protected void configure() {
		bind(DatabaseCleaner.class).to(DatabaseCleanerImpl.class);
		// The executor is bounded, so tasks must be independent and short-lived
		bind(Executor.class).annotatedWith(DatabaseExecutor.class).toInstance(
				new BoundedExecutor(MAX_QUEUED_DB_TASKS, MIN_DB_THREADS,
						MAX_DB_THREADS));
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
