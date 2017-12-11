package org.briarproject.bramble.db;

import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.system.Clock;
import org.junit.Ignore;

import java.sql.Connection;

@Ignore
public class H2DatabasePerformanceSelfComparisonTest
		extends JdbcDatabasePerformanceComparisonTest {

	@Override
	Database<Connection> createDatabase(boolean conditionA,
			DatabaseConfig databaseConfig, Clock clock) {
		return new H2Database(databaseConfig, clock);
	}

	@Override
	protected String getTestName() {
		return H2DatabasePerformanceSelfComparisonTest.class.getSimpleName();
	}
}
