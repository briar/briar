package org.briarproject.bramble.db;

import org.briarproject.bramble.api.db.DbException;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.db.JdbcUtils.tryToClose;

class Migration41_42 implements Migration<Connection> {

	private static final Logger LOG = getLogger(Migration41_42.class.getName());

	private final DatabaseTypes dbTypes;

	Migration41_42(DatabaseTypes dbTypes) {
		this.dbTypes = dbTypes;
	}

	@Override
	public int getStartVersion() {
		return 41;
	}

	@Override
	public int getEndVersion() {
		return 42;
	}

	@Override
	public void migrate(Connection txn) throws DbException {
		Statement s = null;
		try {
			s = txn.createStatement();
			s.execute("ALTER TABLE outgoingKeys"
					+ " ALTER COLUMN rotationPeriod"
					+ " RENAME TO timePeriod");
			s.execute("ALTER TABLE incomingKeys"
					+ " ALTER COLUMN rotationPeriod"
					+ " RENAME TO timePeriod");
			s.execute("ALTER TABLE incomingKeys"
					+ " DROP COLUMN contactId");
			s.execute(dbTypes.replaceTypes("CREATE TABLE pendingContacts"
							+ " (pendingContactId _HASH NOT NULL,"
							+ " PRIMARY KEY (pendingContactId))"));
			s.execute(dbTypes.replaceTypes("CREATE TABLE outgoingStaticKeys"
							+ " (transportId _STRING NOT NULL,"
							+ " staticKeySetId _COUNTER,"
							+ " rootKey _SECRET NOT NULL,"
							+ " timePeriod BIGINT NOT NULL,"
							+ " stream BIGINT NOT NULL,"
							+ " contactId INT," // Null if contact is pending
							+ " pendingContactId _HASH," // Null if not pending
							+ " PRIMARY KEY (transportId, staticKeySetId),"
							+ " FOREIGN KEY (transportId)"
							+ " REFERENCES transports (transportId)"
							+ " ON DELETE CASCADE,"
							+ " UNIQUE (staticKeySetId),"
							+ " FOREIGN KEY (contactId)"
							+ " REFERENCES contacts (contactId)"
							+ " ON DELETE CASCADE,"
							+ " FOREIGN KEY (pendingContactId)"
							+ " REFERENCES pendingContacts (pendingContactId)"
							+ " ON DELETE CASCADE)"));
			s.execute(dbTypes.replaceTypes("CREATE TABLE incomingStaticKeys"
							+ " (transportId _STRING NOT NULL,"
							+ " staticKeySetId INT NOT NULL,"
							+ " timePeriod BIGINT NOT NULL,"
							+ " base BIGINT NOT NULL,"
							+ " bitmap _BINARY NOT NULL,"
							+ " periodOffset INT NOT NULL,"
							+ " PRIMARY KEY (transportId, staticKeySetId,"
							+ " periodOffset),"
							+ " FOREIGN KEY (transportId)"
							+ " REFERENCES transports (transportId)"
							+ " ON DELETE CASCADE,"
							+ " FOREIGN KEY (staticKeySetId)"
							+ " REFERENCES outgoingStaticKeys (staticKeySetId)"
							+ " ON DELETE CASCADE)"));
		} catch (SQLException e) {
			tryToClose(s, LOG, WARNING);
			throw new DbException(e);
		}
	}
}
