package org.briarproject.bramble.api.db;

public interface MigrationListener {

	/**
	 * This is called when a migration is started while opening the database.
	 * It will be called once for each migration being applied.
	 */
	void onDatabaseMigration();

	/**
	 * This is called when compaction is started while opening the database.
	 */
	void onDatabaseCompaction();
}
