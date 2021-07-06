package org.briarproject.bramble.db;

import org.briarproject.bramble.api.db.DbException;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.db.JdbcUtils.tryToClose;

class Migration48_49 implements Migration<Connection> {

	private static final Logger LOG = getLogger(Migration48_49.class.getName());

	@Override
	public int getStartVersion() {
		return 48;
	}

	@Override
	public int getEndVersion() {
		return 49;
	}

	@Override
	public void migrate(Connection txn) throws DbException {
		Statement s = null;
		try {
			s = txn.createStatement();
			s.execute("ALTER TABLE transports"
					+ " ALTER COLUMN maxLatency"
					+ " SET DATA TYPE BIGINT");
		} catch (SQLException e) {
			tryToClose(s, LOG, WARNING);
			throw new DbException(e);
		}
	}
}
