package org.briarproject.bramble.db;

import org.briarproject.bramble.api.db.DbException;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;

class Migration40_41 implements Migration<Connection> {

	private static final Logger LOG = getLogger(Migration40_41.class.getName());

	@Override
	public int getStartVersion() {
		return 40;
	}

	@Override
	public int getEndVersion() {
		return 41;
	}

	@Override
	public void migrate(Connection txn) throws DbException {
		Statement s = null;
		try {
			s = txn.createStatement();
			s.execute("ALTER TABLE contacts"
					// TODO how to insertTypeNames _STRING ?
					+ " ADD alias VARCHAR");
		} catch (SQLException e) {
			tryToClose(s);
			throw new DbException(e);
		}
	}

	private void tryToClose(@Nullable Statement s) {
		try {
			if (s != null) s.close();
		} catch (SQLException e) {
			logException(LOG, WARNING, e);
		}
	}
}
