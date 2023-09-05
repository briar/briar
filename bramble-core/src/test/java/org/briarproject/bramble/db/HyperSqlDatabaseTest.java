package org.briarproject.bramble.db;

import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.sync.MessageFactory;
import org.briarproject.bramble.api.system.Clock;
import org.junit.Before;

import static org.briarproject.bramble.test.TestUtils.isCryptoStrengthUnlimited;
import static org.junit.Assume.assumeTrue;

public class HyperSqlDatabaseTest extends JdbcDatabaseTest {

	@Override
	@Before
	public void setUp() {
		assumeTrue(isCryptoStrengthUnlimited());
		super.setUp();
	}

	@Override
	protected JdbcDatabase createDatabase(DatabaseConfig config,
			MessageFactory messageFactory, Clock clock) {
		return new HyperSqlDatabase(config, messageFactory, clock);
	}

	@Override
	public void testExplainGetMessageIds() {
		// Ugh, HSQLDB can't handle EXPLAIN PLAN FOR in prepared statements
	}
}
