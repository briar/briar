package org.briarproject.bramble.db;

import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.system.Clock;
import org.junit.Ignore;

@Ignore
public class H2DatabasePerformanceTest
		extends JdbcSingleDatabasePerformanceTest {

	@Override
	protected String getTestName() {
		return H2DatabasePerformanceTest.class.getSimpleName();
	}

	@Override
	protected JdbcDatabase createDatabase(DatabaseConfig config, Clock clock) {
		return new H2Database(config, clock);
	}
}
