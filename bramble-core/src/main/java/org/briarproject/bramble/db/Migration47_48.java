package org.briarproject.bramble.db;

import org.briarproject.bramble.api.db.DbException;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.db.JdbcUtils.tryToClose;

class Migration47_48 implements Migration<Connection> {

	private static final Logger LOG = getLogger(Migration47_48.class.getName());

	@Override
	public int getStartVersion() {
		return 47;
	}

	@Override
	public int getEndVersion() {
		return 48;
	}

	@Override
	public void migrate(Connection txn) throws DbException {
		Statement s = null;
		try {
			s = txn.createStatement();
			// Null if no timer duration has been set
			s.execute("ALTER TABLE messages"
					+ " ADD COLUMN cleanupTimerDuration BIGINT");
			// Null if no timer duration has been set or the timer
			// hasn't started
			s.execute("ALTER TABLE messages"
					+ " ADD COLUMN cleanupDeadline BIGINT");
			s.execute("CREATE INDEX IF NOT EXISTS messagesByCleanupDeadline"
					+ " ON messages (cleanupDeadline)");
		} catch (SQLException e) {
			tryToClose(s, LOG, WARNING);
			throw new DbException(e);
		}
	}
}
