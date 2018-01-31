package org.briarproject.bramble.db;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.sql.Connection;
import java.util.Collection;

@NotNullByDefault
public class H2MigrationTest extends DatabaseMigrationTest {

	@Override
	Database<Connection> createDatabase(
			Collection<Migration<Connection>> migrations) throws Exception {
		return new H2Database(config, clock) {
			@Override
			Collection<Migration<Connection>> getMigrations() {
				return migrations;
			}
		};
	}
}
