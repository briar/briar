package org.briarproject.bramble.db;

import org.briarproject.bramble.api.db.DbException;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.db.JdbcUtils.tryToClose;

class Migration46_47 implements Migration<Connection> {

	private static final Logger LOG = getLogger(Migration46_47.class.getName());

	private final DatabaseTypes dbTypes;

	Migration46_47(DatabaseTypes dbTypes) {
		this.dbTypes = dbTypes;
	}

	@Override
	public int getStartVersion() {
		return 46;
	}

	@Override
	public int getEndVersion() {
		return 47;
	}

	@Override
	public void migrate(Connection txn) throws DbException {
		Statement s = null;
		try {
			s = txn.createStatement();
			s.execute(dbTypes.replaceTypes("ALTER TABLE contacts"
					+ " ADD COLUMN syncVersions"
					+ " _BINARY DEFAULT '00' NOT NULL"));
			s.close();
		} catch (SQLException e) {
			tryToClose(s, LOG, WARNING);
			throw new DbException(e);
		}
	}
}
