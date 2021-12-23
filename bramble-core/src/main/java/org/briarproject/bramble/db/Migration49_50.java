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

	private final DatabaseTypes dbTypes;

	Migration49_50(DatabaseTypes dbTypes) {
		this.dbTypes = dbTypes;
	}

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
			s.execute(dbTypes.replaceTypes("CREATE TABLE syncSessionMessages"
					+ " (contactId INT NOT NULL,"
					+ " syncSessionId _HASH NOT NULL,"
					+ " messageId _HASH NOT NULL,"
					+ " acked BOOLEAN NOT NULL,"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE,"
					+ " FOREIGN KEY (messageId)"
					+ " REFERENCES messages (messageId)"
					+ " ON DELETE CASCADE)"));
			s.execute(dbTypes.replaceTypes("CREATE INDEX"
					+ " syncSessionMessagesByContactIdSyncSessionId"
					+ " ON syncSessionMessages (contactId, syncSessionId)"));
		} catch (SQLException e) {
			tryToClose(s, LOG, WARNING);
			throw new DbException(e);
		}
	}
}
