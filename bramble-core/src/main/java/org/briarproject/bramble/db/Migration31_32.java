package org.briarproject.bramble.db;

import org.briarproject.bramble.api.db.DbException;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import static java.util.logging.Level.WARNING;

class Migration31_32 implements Migration<Connection> {

	private static final Logger LOG =
			Logger.getLogger(Migration31_32.class.getName());

	@Override
	public int getStartVersion() {
		return 31;
	}

	@Override
	public int getEndVersion() {
		return 32;
	}

	@Override
	public void migrate(Connection txn) throws DbException {
		Statement s = null;
		try {
			s = txn.createStatement();
			// Add denormalised columns
			s.execute("ALTER TABLE statuses ADD COLUMN"
					+ " (groupId BINARY(32),"
					+ " timestamp BIGINT,"
					+ " length INT,"
					+ " state INT,"
					+ " groupShared BOOLEAN,"
					+ " messageShared BOOLEAN,"
					+ " deleted BOOLEAN)");
			// Populate columns from messages table
			s.execute("UPDATE statuses AS s SET (groupId, timestamp, length,"
					+ " state, messageShared, deleted) ="
					+ " (SELECT groupId, timestamp, length, state, shared,"
					+ " raw IS NULL FROM messages AS m"
					+ " WHERE s.messageId = m.messageId)");
			// Populate column from groupVisibilities table
			s.execute("UPDATE statuses AS s SET groupShared ="
					+ " (SELECT shared FROM groupVisibilities AS gv"
					+ " WHERE s.contactId = gv.contactId"
					+ " AND s.groupId = gv.groupId)");
			// Add not null constraints now columns have been populated
			s.execute("ALTER TABLE statuses ALTER COLUMN groupId SET NOT NULL");
			s.execute("ALTER TABLE statuses ALTER COLUMN timestamp"
					+ " SET NOT NULL");
			s.execute("ALTER TABLE statuses ALTER COLUMN length SET NOT NULL");
			s.execute("ALTER TABLE statuses ALTER COLUMN state SET NOT NULL");
			s.execute("ALTER TABLE statuses ALTER COLUMN groupShared"
					+ " SET NOT NULL");
			s.execute("ALTER TABLE statuses ALTER COLUMN messageShared"
					+ " SET NOT NULL");
			s.execute("ALTER TABLE statuses ALTER COLUMN deleted SET NOT NULL");
			// Add foreign key constraint
			s.execute("ALTER TABLE statuses"
					+ " ADD CONSTRAINT statusesForeignKeyGroupId"
					+ " FOREIGN KEY (groupId)"
					+ " REFERENCES groups (groupId)"
					+ " ON DELETE CASCADE");
		} catch (SQLException e) {
			tryToClose(s);
			throw new DbException(e);
		}
	}

	private void tryToClose(@Nullable Statement s) {
		try {
			if (s != null) s.close();
		} catch (SQLException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}
}
