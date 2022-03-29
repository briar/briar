package org.briarproject.bramble.db;

import org.briarproject.bramble.api.db.DbException;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.db.JdbcUtils.tryToClose;

class Migration49_50 implements Migration<Connection> {

	private static final Logger LOG = getLogger(Migration49_50.class.getName());

	@Override
	public int getStartVersion() {
		return 49;
	}

	@Override
	public int getEndVersion() {
		return 50;
	}

	@Override
	public void migrate(Connection txn) throws DbException {
		Statement s = null;
		try {
			s = txn.createStatement();
			s.execute("ALTER TABLE statuses"
					+ " ALTER COLUMN eta"
					+ " RENAME TO maxLatency");
			s.execute("ALTER TABLE statuses"
					+ " ALTER COLUMN maxLatency"
					+ " SET NULL");
			s.execute("UPDATE statuses SET maxLatency = NULL");
		} catch (SQLException e) {
			tryToClose(s, LOG, WARNING);
			throw new DbException(e);
		}
	}
}
