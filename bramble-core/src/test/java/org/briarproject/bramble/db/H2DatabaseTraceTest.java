package org.briarproject.bramble.db;

import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.system.Clock;
import org.junit.Ignore;

import java.io.File;
import java.sql.Connection;

import javax.annotation.Nonnull;

@Ignore
public class H2DatabaseTraceTest extends DatabaseTraceTest {

	@Override
	Database<Connection> createDatabase(DatabaseConfig databaseConfig,
			Clock clock) {
		return new H2Database(databaseConfig, clock) {
			@Override
			@Nonnull
			String getUrl() {
				return super.getUrl() + ";TRACE_LEVEL_FILE=3";
			}
		};
	}

	@Override
	protected File getTraceFile() {
		return new File(testDir, "db.trace.db");
	}

	@Override
	protected String getTestName() {
		return getClass().getSimpleName();
	}
}
