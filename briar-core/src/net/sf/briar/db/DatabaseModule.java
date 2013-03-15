package net.sf.briar.db;

import java.sql.Connection;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

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

	@Override
	protected void configure() {
		bind(DatabaseCleaner.class).to(DatabaseCleanerImpl.class);
		bind(Executor.class).annotatedWith(DatabaseExecutor.class).toInstance(
				Executors.newCachedThreadPool());
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
