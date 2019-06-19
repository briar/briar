package org.briarproject.bramble.db;

import org.briarproject.bramble.api.db.DbException;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.db.JdbcUtils.tryToClose;

class Migration45_46 implements Migration<Connection> {

	private static final Logger LOG = getLogger(Migration45_46.class.getName());

	@Override
	public int getStartVersion() {
		return 45;
	}

	@Override
	public int getEndVersion() {
		return 46;
	}

	@Override
	public void migrate(Connection txn) throws DbException {
		Statement s = null;
		try {
			s = txn.createStatement();
			s.execute("ALTER TABLE messages"
					+ " ADD COLUMN temporary BOOLEAN DEFAULT FALSE NOT NULL");
			s.close();
		} catch (SQLException e) {
			tryToClose(s, LOG, WARNING);
			throw new DbException(e);
		}
	}
}