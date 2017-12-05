package org.briarproject.bramble.db;

import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.system.Clock;

public class HyperSqlDatabaseTest extends JdbcDatabaseTest {

	public HyperSqlDatabaseTest() throws Exception {
		super();
	}

	@Override
	protected JdbcDatabase createDatabase(DatabaseConfig config, Clock clock) {
		return new HyperSqlDatabase(config, clock);
	}
}
