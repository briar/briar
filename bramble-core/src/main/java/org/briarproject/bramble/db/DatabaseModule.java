package org.briarproject.bramble.db;

import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.lifecycle.ShutdownManager;
import org.briarproject.bramble.api.sync.MessageFactory;
import org.briarproject.bramble.api.system.Clock;

import java.sql.Connection;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class DatabaseModule {

	@Provides
	@Singleton
	Database<Connection> provideDatabase(DatabaseConfig config,
			MessageFactory messageFactory, Clock clock) {
		return new H2Database(config, messageFactory, clock);
	}

	@Provides
	@Singleton
	DatabaseComponent provideDatabaseComponent(Database<Connection> db,
			EventBus eventBus, ShutdownManager shutdown) {
		return new DatabaseComponentImpl<>(db, Connection.class, eventBus,
				shutdown);
	}
}
