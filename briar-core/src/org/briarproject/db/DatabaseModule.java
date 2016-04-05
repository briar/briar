package org.briarproject.db;

import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DatabaseConfig;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.lifecycle.ShutdownManager;
import org.briarproject.api.system.Clock;

import java.security.SecureRandom;
import java.sql.Connection;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class DatabaseModule {

	@Provides
	@Singleton
	Database<Connection> provideDatabase(DatabaseConfig config,
			SecureRandom random, Clock clock) {
		return new H2Database(config, random, clock);
	}

	@Provides
	@Singleton
	DatabaseComponent provideDatabaseComponent(Database<Connection> db,
			EventBus eventBus, ShutdownManager shutdown) {
		return new DatabaseComponentImpl<Connection>(db, Connection.class,
				eventBus, shutdown);
	}
}
