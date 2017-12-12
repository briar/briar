package org.briarproject.bramble.db;

import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.Clock;
import org.junit.Ignore;

import java.sql.Connection;

/**
 * Sanity check for {@link DatabasePerformanceComparisonTest}: check that
 * if condition B sleeps for 1ms before every commit, condition A is
 * considered to be faster.
 */
@Ignore
public class H2SleepDatabasePerformanceComparisonTest
		extends DatabasePerformanceComparisonTest {

	@Override
	Database<Connection> createDatabase(boolean conditionA,
			DatabaseConfig databaseConfig, Clock clock) {
		if (conditionA) {
			return new H2Database(databaseConfig, clock);
		} else {
			return new H2Database(databaseConfig, clock) {
				@Override
				@NotNullByDefault
				public void commitTransaction(Connection txn)
						throws DbException {
					try {
						Thread.sleep(1);
					} catch (InterruptedException e) {
						throw new DbException(e);
					}
					super.commitTransaction(txn);
				}
			};
		}
	}

	@Override
	protected String getTestName() {
		return getClass().getSimpleName();
	}
}
