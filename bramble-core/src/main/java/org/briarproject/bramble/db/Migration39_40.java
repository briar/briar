package org.briarproject.bramble.db;

import org.briarproject.bramble.api.db.DbException;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logException;

class Migration39_40 implements Migration<Connection> {

	private static final Logger LOG =
			Logger.getLogger(Migration39_40.class.getName());

	@Override
	public int getStartVersion() {
		return 39;
	}

	@Override
	public int getEndVersion() {
		return 40;
	}

	@Override
	public void migrate(Connection txn) throws DbException {
		Statement s = null;
		try {
			s = txn.createStatement();
			s.execute("ALTER TABLE statuses"
					+ " ADD eta BIGINT");
			s.execute("UPDATE statuses SET eta = 0");
			s.execute("ALTER TABLE statuses"
					+ " ALTER COLUMN eta"
					+ " SET NOT NULL");
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
