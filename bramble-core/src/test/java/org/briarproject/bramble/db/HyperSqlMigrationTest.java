package org.briarproject.bramble.db;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.sql.Connection;
import java.util.Collection;

@NotNullByDefault
public class HyperSqlMigrationTest extends DatabaseMigrationTest {

	@Override
	Database<Connection> createDatabase(
			Collection<Migration<Connection>> migrations) throws Exception {
		return new HyperSqlDatabase(config, clock) {
			@Override
			Collection<Migration<Connection>> getMigrations() {
				return migrations;
			}
		};
	}
}
