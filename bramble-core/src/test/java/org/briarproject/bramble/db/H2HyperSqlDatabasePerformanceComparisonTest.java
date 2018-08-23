package org.briarproject.bramble.db;

import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.system.Clock;
import org.junit.Ignore;

import java.sql.Connection;

@Ignore
public class H2HyperSqlDatabasePerformanceComparisonTest
		extends DatabasePerformanceComparisonTest {

	@Override
	Database<Connection> createDatabase(boolean conditionA,
			DatabaseConfig databaseConfig,
			Clock clock) {
		if (conditionA)
			return new H2Database(databaseConfig, clock);
		else return new HyperSqlDatabase(databaseConfig, clock);
	}

	@Override
	protected String getTestName() {
		return getClass().getSimpleName();
	}
}
