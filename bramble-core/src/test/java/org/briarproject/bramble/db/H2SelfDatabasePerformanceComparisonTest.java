package org.briarproject.bramble.db;

import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.system.Clock;
import org.junit.Ignore;

import java.sql.Connection;

/**
 * Sanity check for {@link DatabasePerformanceComparisonTest}: check that
 * if conditions A and B are identical, no significant difference is (usually)
 * detected.
 */
@Ignore
public class H2SelfDatabasePerformanceComparisonTest
		extends DatabasePerformanceComparisonTest {

	@Override
	Database<Connection> createDatabase(boolean conditionA,
			DatabaseConfig databaseConfig,
			Clock clock) {
		return new H2Database(databaseConfig, clock);
	}

	@Override
	protected String getTestName() {
		return getClass().getSimpleName();
	}
}
