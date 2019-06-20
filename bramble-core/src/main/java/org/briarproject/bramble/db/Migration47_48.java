package org.briarproject.bramble.db;

import org.briarproject.bramble.api.db.DbException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

import static java.lang.System.arraycopy;
import static java.sql.Types.BINARY;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;
import static org.briarproject.bramble.db.JdbcUtils.tryToClose;

class Migration47_48 implements Migration<Connection> {

	private static final Logger LOG = getLogger(Migration47_48.class.getName());

	private final DatabaseTypes dbTypes;

	Migration47_48(DatabaseTypes dbTypes) {
		this.dbTypes = dbTypes;
	}

	@Override
	public int getStartVersion() {
		return 47;
	}

	@Override
	public int getEndVersion() {
		return 48;
	}

	@Override
	public void migrate(Connection txn) throws DbException {
		Statement s = null;
		ResultSet rs = null;
		PreparedStatement ps = null;
		try {
			s = txn.createStatement();
			s.execute("ALTER TABLE messages"
					+ " ADD COLUMN deleted BOOLEAN DEFAULT FALSE NOT NULL");
			s.execute("UPDATE messages SET deleted = TRUE WHERE raw IS NULL");
			s.execute("ALTER TABLE messages"
					+ " ADD COLUMN blockCount INT DEFAULT 1 NOT NULL");
			s.execute(dbTypes.replaceTypes("CREATE TABLE blocks"
					+ " (messageId _HASH NOT NULL,"
					+ " blockNumber INT NOT NULL,"
					+ " blockLength INT NOT NULL," // Excludes block header
					+ " data BLOB," // Null if message has been deleted
					+ " PRIMARY KEY (messageId, blockNumber),"
					+ " FOREIGN KEY (messageId)"
					+ " REFERENCES messages (messageId)"
					+ " ON DELETE CASCADE)"));
			rs = s.executeQuery("SELECT messageId, length, raw FROM messages");
			ps = txn.prepareStatement("INSERT INTO blocks"
					+ " (messageId, blockNumber, blockLength, data)"
					+ " VALUES (?, 0, ?, ?)");
			int migrated = 0;
			while (rs.next()) {
				byte[] id = rs.getBytes(1);
				int length = rs.getInt(2);
				byte[] raw = rs.getBytes(3);
				ps.setBytes(1, id);
				ps.setInt(2, length - MESSAGE_HEADER_LENGTH);
				if (raw == null) {
					ps.setNull(3, BINARY);
				} else {
					byte[] data = new byte[raw.length - MESSAGE_HEADER_LENGTH];
					arraycopy(raw, MESSAGE_HEADER_LENGTH, data, 0, data.length);
					ps.setBytes(3, data);
				}
				if (ps.executeUpdate() != 1) throw new DbStateException();
				migrated++;
			}
			ps.close();
			rs.close();
			s.execute("ALTER TABLE messages DROP COLUMN raw");
			s.close();
			if (LOG.isLoggable(INFO))
				LOG.info("Migrated " + migrated + " messages");
		} catch (SQLException e) {
			tryToClose(ps, LOG, WARNING);
			tryToClose(rs, LOG, WARNING);
			tryToClose(s, LOG, WARNING);
			throw new DbException(e);
		}
	}
}
