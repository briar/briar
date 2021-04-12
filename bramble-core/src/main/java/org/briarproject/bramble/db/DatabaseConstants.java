package org.briarproject.bramble.db;

import org.briarproject.bramble.api.settings.Settings;

import static java.util.concurrent.TimeUnit.DAYS;

interface DatabaseConstants {

	/**
	 * The maximum number of offered messages from each contact that will be
	 * stored. If offers arrive more quickly than requests can be sent and this
	 * limit is reached, additional offers will not be stored.
	 */
	int MAX_OFFERED_MESSAGES = 1000;

	/**
	 * The namespace of the {@link Settings} where the database schema version
	 * is stored.
	 */
	String DB_SETTINGS_NAMESPACE = "db";

	/**
	 * The {@link Settings} key under which the database schema version is
	 * stored.
	 */
	String SCHEMA_VERSION_KEY = "schemaVersion";

	/**
	 * The {@link Settings} key under which the time of the last database
	 * compaction is stored.
	 */
	String LAST_COMPACTED_KEY = "lastCompacted";

	/**
	 * The maximum time between database compactions in milliseconds. When the
	 * database is opened it will be compacted if more than this amount of time
	 * has passed since the last compaction.
	 */
	long MAX_COMPACTION_INTERVAL_MS = DAYS.toMillis(30);

	/**
	 * The {@link Settings} key under which the flag is stored indicating
	 * whether the database is marked as dirty.
	 */
	String DIRTY_KEY = "dirty";
}
