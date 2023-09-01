package org.briarproject.bramble.db;

import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.sync.MessageFactory;
import org.briarproject.bramble.api.system.Clock;
import org.junit.BeforeClass;

import java.sql.Connection;

import static org.briarproject.bramble.test.TestUtils.isOptionalTestEnabled;
import static org.junit.Assume.assumeTrue;

public class H2SqliteDatabasePerformanceComparisonTest
		extends DatabasePerformanceComparisonTest {

	@BeforeClass
	public static void setUpClass() {
		assumeTrue(isOptionalTestEnabled(
				H2SqliteDatabasePerformanceComparisonTest.class));
	}

	@Override
	Database<Connection> createDatabase(boolean conditionA,
			DatabaseConfig databaseConfig, MessageFactory messageFactory,
			Clock clock) {
		if (conditionA) {
			return new H2Database(databaseConfig, messageFactory, clock);
		} else {
			return new SqliteDatabase(databaseConfig, messageFactory, clock);
		}
	}

	@Override
	protected String getTestName() {
		return getClass().getSimpleName();
	}
}
