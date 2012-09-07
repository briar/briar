package net.sf.briar.db;

import java.io.File;
import java.sql.Connection;
import java.util.concurrent.Executor;

import net.sf.briar.api.clock.Clock;
import net.sf.briar.api.crypto.Password;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseDirectory;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.db.DatabaseMaxSize;
import net.sf.briar.api.db.DatabasePassword;
import net.sf.briar.api.lifecycle.ShutdownManager;
import net.sf.briar.api.protocol.GroupFactory;
import net.sf.briar.api.protocol.PacketFactory;
import net.sf.briar.api.transport.ConnectionContextFactory;
import net.sf.briar.api.transport.ConnectionWindowFactory;
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
	Database<Connection> getDatabase(@DatabaseDirectory File dir,
			@DatabasePassword Password password, @DatabaseMaxSize long maxSize,
			ConnectionContextFactory connectionContextFactory,
			ConnectionWindowFactory connectionWindowFactory,
			GroupFactory groupFactory, Clock clock) {
		return new H2Database(dir, password, maxSize, connectionContextFactory,
				connectionWindowFactory, groupFactory, clock);
	}

	@Provides @Singleton
	DatabaseComponent getDatabaseComponent(Database<Connection> db,
			DatabaseCleaner cleaner, ShutdownManager shutdown,
			PacketFactory packetFactory, Clock clock) {
		return new DatabaseComponentImpl<Connection>(db, cleaner, shutdown,
				packetFactory, clock);
	}
}
