package org.briarproject.bramble.db;

import org.briarproject.bramble.api.db.DbException;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import static java.util.logging.Level.WARNING;

class Migration30_31 implements Migration<Connection> {

	private static final Logger LOG =
			Logger.getLogger(Migration30_31.class.getName());

	@Override
	public int getStartVersion() {
		return 30;
	}

	@Override
	public int getEndVersion() {
		return 31;
	}

	@Override
	public void migrate(Connection txn) throws DbException {
		Statement s = null;
		try {
			s = txn.createStatement();
			// Add groupId column
			s.execute("ALTER TABLE messageMetadata"
					+ " ADD COLUMN groupId BINARY(32) AFTER messageId");
			// Populate groupId column
			s.execute("UPDATE messageMetadata AS mm SET groupId ="
					+ " (SELECT groupId FROM messages AS m"
					+ " WHERE mm.messageId = m.messageId)");
			// Add not null constraint now column has been populated
			s.execute("ALTER TABLE messageMetadata"
					+ " ALTER COLUMN groupId"
					+ " SET NOT NULL");
			// Add foreign key constraint
			s.execute("ALTER TABLE messageMetadata"
					+ " ADD CONSTRAINT groupIdForeignKey"
					+ " FOREIGN KEY (groupId)"
					+ " REFERENCES groups (groupId)"
					+ " ON DELETE CASCADE");
			// Add state column
			s.execute("ALTER TABLE messageMetadata"
					+ " ADD COLUMN state INT AFTER groupId");
			// Populate state column
			s.execute("UPDATE messageMetadata AS mm SET state ="
					+ " (SELECT state FROM messages AS m"
					+ " WHERE mm.messageId = m.messageId)");
			// Add not null constraint now column has been populated
			s.execute("ALTER TABLE messageMetadata"
					+ " ALTER COLUMN state"
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
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}
}
