package org.briarproject.bramble.db;

import org.briarproject.bramble.api.db.DbException;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.db.JdbcUtils.tryToClose;

class Migration42_43 implements Migration<Connection> {

	private static final Logger LOG = getLogger(Migration42_43.class.getName());

	private final DatabaseTypes dbTypes;

	Migration42_43(DatabaseTypes dbTypes) {
		this.dbTypes = dbTypes;
	}

	@Override
	public int getStartVersion() {
		return 42;
	}

	@Override
	public int getEndVersion() {
		return 43;
	}

	@Override
	public void migrate(Connection txn) throws DbException {
		Statement s = null;
		try {
			s = txn.createStatement();
			s.execute(dbTypes.replaceTypes("ALTER TABLE localAuthors"
					+ " ADD COLUMN handshakePublicKey _BINARY"));
			s.execute(dbTypes.replaceTypes("ALTER TABLE localAuthors"
					+ " ADD COLUMN handshakePrivateKey _BINARY"));
			s.execute(dbTypes.replaceTypes("ALTER TABLE contacts"
					+ " ADD COLUMN handshakePublicKey _BINARY"));
			s.execute("ALTER TABLE contacts"
					+ " DROP COLUMN active");
			s.close();
		} catch (SQLException e) {
			tryToClose(s, LOG, WARNING);
			throw new DbException(e);
		}
	}
}
