package org.briarproject.bramble.db;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DataTooNewException;
import org.briarproject.bramble.api.db.DataTooOldException;
import org.briarproject.bramble.api.db.DbClosedException;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.db.MigrationListener;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.Group.Visibility;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.MessageStatus;
import org.briarproject.bramble.api.sync.ValidationManager.State;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.transport.IncomingKeys;
import org.briarproject.bramble.api.transport.KeySet;
import org.briarproject.bramble.api.transport.KeySetId;
import org.briarproject.bramble.api.transport.OutgoingKeys;
import org.briarproject.bramble.api.transport.TransportKeys;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import static java.sql.Types.INTEGER;
import static java.util.Collections.singletonList;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.api.db.Metadata.REMOVE;
import static org.briarproject.bramble.api.sync.Group.Visibility.INVISIBLE;
import static org.briarproject.bramble.api.sync.Group.Visibility.SHARED;
import static org.briarproject.bramble.api.sync.Group.Visibility.VISIBLE;
import static org.briarproject.bramble.api.sync.ValidationManager.State.DELIVERED;
import static org.briarproject.bramble.api.sync.ValidationManager.State.PENDING;
import static org.briarproject.bramble.api.sync.ValidationManager.State.UNKNOWN;
import static org.briarproject.bramble.db.DatabaseConstants.DB_SETTINGS_NAMESPACE;
import static org.briarproject.bramble.db.DatabaseConstants.SCHEMA_VERSION_KEY;
import static org.briarproject.bramble.db.ExponentialBackoff.calculateExpiry;
import static org.briarproject.bramble.util.LogUtils.logException;

/**
 * A generic database implementation that can be used with any JDBC-compatible
 * database library.
 */
@NotNullByDefault
abstract class JdbcDatabase implements Database<Connection> {

	// Package access for testing
	static final int CODE_SCHEMA_VERSION = 39;

	// Rotation period offsets for incoming transport keys
	private static final int OFFSET_PREV = -1;
	private static final int OFFSET_CURR = 0;
	private static final int OFFSET_NEXT = 1;

	private static final String CREATE_SETTINGS =
			"CREATE TABLE settings"
					+ " (namespace _STRING NOT NULL,"
					+ " settingKey _STRING NOT NULL,"
					+ " value _STRING NOT NULL,"
					+ " PRIMARY KEY (namespace, settingKey))";

	private static final String CREATE_LOCAL_AUTHORS =
			"CREATE TABLE localAuthors"
					+ " (authorId _HASH NOT NULL,"
					+ " formatVersion INT NOT NULL,"
					+ " name _STRING NOT NULL,"
					+ " publicKey _BINARY NOT NULL,"
					+ " privateKey _BINARY NOT NULL,"
					+ " created BIGINT NOT NULL,"
					+ " PRIMARY KEY (authorId))";

	private static final String CREATE_CONTACTS =
			"CREATE TABLE contacts"
					+ " (contactId _COUNTER,"
					+ " authorId _HASH NOT NULL,"
					+ " formatVersion INT NOT NULL,"
					+ " name _STRING NOT NULL,"
					+ " publicKey _BINARY NOT NULL,"
					+ " localAuthorId _HASH NOT NULL,"
					+ " verified BOOLEAN NOT NULL,"
					+ " active BOOLEAN NOT NULL,"
					+ " PRIMARY KEY (contactId),"
					+ " FOREIGN KEY (localAuthorId)"
					+ " REFERENCES localAuthors (authorId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_GROUPS =
			"CREATE TABLE groups"
					+ " (groupId _HASH NOT NULL,"
					+ " clientId _STRING NOT NULL,"
					+ " majorVersion INT NOT NULL,"
					+ " descriptor _BINARY NOT NULL,"
					+ " PRIMARY KEY (groupId))";

	private static final String CREATE_GROUP_METADATA =
			"CREATE TABLE groupMetadata"
					+ " (groupId _HASH NOT NULL,"
					+ " metaKey _STRING NOT NULL,"
					+ " value _BINARY NOT NULL,"
					+ " PRIMARY KEY (groupId, metaKey),"
					+ " FOREIGN KEY (groupId)"
					+ " REFERENCES groups (groupId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_GROUP_VISIBILITIES =
			"CREATE TABLE groupVisibilities"
					+ " (contactId INT NOT NULL,"
					+ " groupId _HASH NOT NULL,"
					+ " shared BOOLEAN NOT NULL,"
					+ " PRIMARY KEY (contactId, groupId),"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE,"
					+ " FOREIGN KEY (groupId)"
					+ " REFERENCES groups (groupId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_MESSAGES =
			"CREATE TABLE messages"
					+ " (messageId _HASH NOT NULL,"
					+ " groupId _HASH NOT NULL,"
					+ " timestamp BIGINT NOT NULL,"
					+ " state INT NOT NULL,"
					+ " shared BOOLEAN NOT NULL,"
					+ " length INT NOT NULL,"
					+ " raw BLOB," // Null if message has been deleted
					+ " PRIMARY KEY (messageId),"
					+ " FOREIGN KEY (groupId)"
					+ " REFERENCES groups (groupId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_MESSAGE_METADATA =
			"CREATE TABLE messageMetadata"
					+ " (messageId _HASH NOT NULL,"
					+ " groupId _HASH NOT NULL," // Denormalised
					+ " state INT NOT NULL," // Denormalised
					+ " metaKey _STRING NOT NULL,"
					+ " value _BINARY NOT NULL,"
					+ " PRIMARY KEY (messageId, metaKey),"
					+ " FOREIGN KEY (messageId)"
					+ " REFERENCES messages (messageId)"
					+ " ON DELETE CASCADE,"
					+ " FOREIGN KEY (groupId)"
					+ " REFERENCES groups (groupId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_MESSAGE_DEPENDENCIES =
			"CREATE TABLE messageDependencies"
					+ " (groupId _HASH NOT NULL,"
					+ " messageId _HASH NOT NULL,"
					+ " dependencyId _HASH NOT NULL," // Not a foreign key
					+ " messageState INT NOT NULL," // Denormalised
					// Denormalised, null if dependency is missing or in a
					// different group
					+ " dependencyState INT,"
					+ " FOREIGN KEY (groupId)"
					+ " REFERENCES groups (groupId)"
					+ " ON DELETE CASCADE,"
					+ " FOREIGN KEY (messageId)"
					+ " REFERENCES messages (messageId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_OFFERS =
			"CREATE TABLE offers"
					+ " (messageId _HASH NOT NULL," // Not a foreign key
					+ " contactId INT NOT NULL,"
					+ " PRIMARY KEY (messageId, contactId),"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_STATUSES =
			"CREATE TABLE statuses"
					+ " (messageId _HASH NOT NULL,"
					+ " contactId INT NOT NULL,"
					+ " groupId _HASH NOT NULL," // Denormalised
					+ " timestamp BIGINT NOT NULL," // Denormalised
					+ " length INT NOT NULL," // Denormalised
					+ " state INT NOT NULL," // Denormalised
					+ " groupShared BOOLEAN NOT NULL," // Denormalised
					+ " messageShared BOOLEAN NOT NULL," // Denormalised
					+ " deleted BOOLEAN NOT NULL," // Denormalised
					+ " ack BOOLEAN NOT NULL,"
					+ " seen BOOLEAN NOT NULL,"
					+ " requested BOOLEAN NOT NULL,"
					+ " expiry BIGINT NOT NULL,"
					+ " txCount INT NOT NULL,"
					+ " PRIMARY KEY (messageId, contactId),"
					+ " FOREIGN KEY (messageId)"
					+ " REFERENCES messages (messageId)"
					+ " ON DELETE CASCADE,"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE,"
					+ " FOREIGN KEY (groupId)"
					+ " REFERENCES groups (groupId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_TRANSPORTS =
			"CREATE TABLE transports"
					+ " (transportId _STRING NOT NULL,"
					+ " maxLatency INT NOT NULL,"
					+ " PRIMARY KEY (transportId))";

	private static final String CREATE_OUTGOING_KEYS =
			"CREATE TABLE outgoingKeys"
					+ " (transportId _STRING NOT NULL,"
					+ " keySetId _COUNTER,"
					+ " rotationPeriod BIGINT NOT NULL,"
					+ " contactId INT NOT NULL,"
					+ " tagKey _SECRET NOT NULL,"
					+ " headerKey _SECRET NOT NULL,"
					+ " stream BIGINT NOT NULL,"
					+ " active BOOLEAN NOT NULL,"
					+ " PRIMARY KEY (transportId, keySetId),"
					+ " FOREIGN KEY (transportId)"
					+ " REFERENCES transports (transportId)"
					+ " ON DELETE CASCADE,"
					+ " UNIQUE (keySetId),"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_INCOMING_KEYS =
			"CREATE TABLE incomingKeys"
					+ " (transportId _STRING NOT NULL,"
					+ " keySetId INT NOT NULL,"
					+ " rotationPeriod BIGINT NOT NULL,"
					+ " contactId INT NOT NULL,"
					+ " tagKey _SECRET NOT NULL,"
					+ " headerKey _SECRET NOT NULL,"
					+ " base BIGINT NOT NULL,"
					+ " bitmap _BINARY NOT NULL,"
					+ " periodOffset INT NOT NULL,"
					+ " PRIMARY KEY (transportId, keySetId, periodOffset),"
					+ " FOREIGN KEY (transportId)"
					+ " REFERENCES transports (transportId)"
					+ " ON DELETE CASCADE,"
					+ " FOREIGN KEY (keySetId)"
					+ " REFERENCES outgoingKeys (keySetId)"
					+ " ON DELETE CASCADE,"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE)";

	private static final String INDEX_CONTACTS_BY_AUTHOR_ID =
			"CREATE INDEX IF NOT EXISTS contactsByAuthorId"
					+ " ON contacts (authorId)";

	private static final String INDEX_GROUPS_BY_CLIENT_ID_MAJOR_VERSION =
			"CREATE INDEX IF NOT EXISTS groupsByClientIdMajorVersion"
					+ " ON groups (clientId, majorVersion)";

	private static final String INDEX_MESSAGE_METADATA_BY_GROUP_ID_STATE =
			"CREATE INDEX IF NOT EXISTS messageMetadataByGroupIdState"
					+ " ON messageMetadata (groupId, state)";

	private static final String INDEX_MESSAGE_DEPENDENCIES_BY_DEPENDENCY_ID =
			"CREATE INDEX IF NOT EXISTS messageDependenciesByDependencyId"
					+ " ON messageDependencies (dependencyId)";

	private static final String INDEX_STATUSES_BY_CONTACT_ID_GROUP_ID =
			"CREATE INDEX IF NOT EXISTS statusesByContactIdGroupId"
					+ " ON statuses (contactId, groupId)";

	private static final String INDEX_STATUSES_BY_CONTACT_ID_TIMESTAMP =
			"CREATE INDEX IF NOT EXISTS statusesByContactIdTimestamp"
					+ " ON statuses (contactId, timestamp)";

	private static final Logger LOG =
			Logger.getLogger(JdbcDatabase.class.getName());

	// Different database libraries use different names for certain types
	private final String hashType, secretType, binaryType;
	private final String counterType, stringType;
	private final Clock clock;

	// Locking: connectionsLock
	private final LinkedList<Connection> connections = new LinkedList<>();

	private int openConnections = 0; // Locking: connectionsLock
	private boolean closed = false; // Locking: connectionsLock

	@Nullable
	protected abstract Connection createConnection() throws SQLException;

	private final Lock connectionsLock = new ReentrantLock();
	private final Condition connectionsChanged = connectionsLock.newCondition();

	JdbcDatabase(String hashType, String secretType, String binaryType,
			String counterType, String stringType, Clock clock) {
		this.hashType = hashType;
		this.secretType = secretType;
		this.binaryType = binaryType;
		this.counterType = counterType;
		this.stringType = stringType;
		this.clock = clock;
	}

	protected void open(String driverClass, boolean reopen,
			@Nullable MigrationListener listener) throws DbException {
		// Load the JDBC driver
		try {
			Class.forName(driverClass);
		} catch (ClassNotFoundException e) {
			throw new DbException(e);
		}
		// Open the database and create the tables and indexes if necessary
		Connection txn = startTransaction();
		try {
			if (reopen) {
				checkSchemaVersion(txn, listener);
			} else {
				createTables(txn);
				storeSchemaVersion(txn, CODE_SCHEMA_VERSION);
			}
			createIndexes(txn);
			commitTransaction(txn);
		} catch (DbException e) {
			abortTransaction(txn);
			throw e;
		}
	}

	/**
	 * Compares the schema version stored in the database with the schema
	 * version used by the current code and applies any suitable migrations to
	 * the data if necessary.
	 *
	 * @throws DataTooNewException if the data uses a newer schema than the
	 * current code
	 * @throws DataTooOldException if the data uses an older schema than the
	 * current code and cannot be migrated
	 */
	private void checkSchemaVersion(Connection txn,
			@Nullable MigrationListener listener) throws DbException {
		Settings s = getSettings(txn, DB_SETTINGS_NAMESPACE);
		int dataSchemaVersion = s.getInt(SCHEMA_VERSION_KEY, -1);
		if (dataSchemaVersion == -1) throw new DbException();
		if (dataSchemaVersion == CODE_SCHEMA_VERSION) return;
		if (CODE_SCHEMA_VERSION < dataSchemaVersion)
			throw new DataTooNewException();
		// Apply any suitable migrations in order
		for (Migration<Connection> m : getMigrations()) {
			int start = m.getStartVersion(), end = m.getEndVersion();
			if (start == dataSchemaVersion) {
				if (LOG.isLoggable(INFO))
					LOG.info("Migrating from schema " + start + " to " + end);
				if (listener != null) listener.onMigrationRun();
				// Apply the migration
				m.migrate(txn);
				// Store the new schema version
				storeSchemaVersion(txn, end);
				dataSchemaVersion = end;
			}
		}
		if (dataSchemaVersion != CODE_SCHEMA_VERSION)
			throw new DataTooOldException();
	}

	// Package access for testing
	List<Migration<Connection>> getMigrations() {
		return singletonList(new Migration38_39());
	}

	private void storeSchemaVersion(Connection txn, int version)
			throws DbException {
		Settings s = new Settings();
		s.putInt(SCHEMA_VERSION_KEY, version);
		mergeSettings(txn, s, DB_SETTINGS_NAMESPACE);
	}

	private void tryToClose(@Nullable ResultSet rs) {
		try {
			if (rs != null) rs.close();
		} catch (SQLException e) {
			logException(LOG, WARNING, e);
		}
	}

	private void tryToClose(@Nullable Statement s) {
		try {
			if (s != null) s.close();
		} catch (SQLException e) {
			logException(LOG, WARNING, e);
		}
	}

	private void createTables(Connection txn) throws DbException {
		Statement s = null;
		try {
			s = txn.createStatement();
			s.executeUpdate(insertTypeNames(CREATE_SETTINGS));
			s.executeUpdate(insertTypeNames(CREATE_LOCAL_AUTHORS));
			s.executeUpdate(insertTypeNames(CREATE_CONTACTS));
			s.executeUpdate(insertTypeNames(CREATE_GROUPS));
			s.executeUpdate(insertTypeNames(CREATE_GROUP_METADATA));
			s.executeUpdate(insertTypeNames(CREATE_GROUP_VISIBILITIES));
			s.executeUpdate(insertTypeNames(CREATE_MESSAGES));
			s.executeUpdate(insertTypeNames(CREATE_MESSAGE_METADATA));
			s.executeUpdate(insertTypeNames(CREATE_MESSAGE_DEPENDENCIES));
			s.executeUpdate(insertTypeNames(CREATE_OFFERS));
			s.executeUpdate(insertTypeNames(CREATE_STATUSES));
			s.executeUpdate(insertTypeNames(CREATE_TRANSPORTS));
			s.executeUpdate(insertTypeNames(CREATE_OUTGOING_KEYS));
			s.executeUpdate(insertTypeNames(CREATE_INCOMING_KEYS));
			s.close();
		} catch (SQLException e) {
			tryToClose(s);
			throw new DbException(e);
		}
	}

	private void createIndexes(Connection txn) throws DbException {
		Statement s = null;
		try {
			s = txn.createStatement();
			s.executeUpdate(INDEX_CONTACTS_BY_AUTHOR_ID);
			s.executeUpdate(INDEX_GROUPS_BY_CLIENT_ID_MAJOR_VERSION);
			s.executeUpdate(INDEX_MESSAGE_METADATA_BY_GROUP_ID_STATE);
			s.executeUpdate(INDEX_MESSAGE_DEPENDENCIES_BY_DEPENDENCY_ID);
			s.executeUpdate(INDEX_STATUSES_BY_CONTACT_ID_GROUP_ID);
			s.executeUpdate(INDEX_STATUSES_BY_CONTACT_ID_TIMESTAMP);
			s.close();
		} catch (SQLException e) {
			tryToClose(s);
			throw new DbException(e);
		}
	}

	private String insertTypeNames(String s) {
		s = s.replaceAll("_HASH", hashType);
		s = s.replaceAll("_SECRET", secretType);
		s = s.replaceAll("_BINARY", binaryType);
		s = s.replaceAll("_COUNTER", counterType);
		s = s.replaceAll("_STRING", stringType);
		return s;
	}

	@Override
	public Connection startTransaction() throws DbException {
		Connection txn;
		connectionsLock.lock();
		try {
			if (closed) throw new DbClosedException();
			txn = connections.poll();
		} finally {
			connectionsLock.unlock();
		}
		try {
			if (txn == null) {
				// Open a new connection
				txn = createConnection();
				if (txn == null) throw new DbException();
				txn.setAutoCommit(false);
				connectionsLock.lock();
				try {
					openConnections++;
				} finally {
					connectionsLock.unlock();
				}
			}
		} catch (SQLException e) {
			throw new DbException(e);
		}
		return txn;
	}

	@Override
	public void abortTransaction(Connection txn) {
		try {
			txn.rollback();
			connectionsLock.lock();
			try {
				connections.add(txn);
				connectionsChanged.signalAll();
			} finally {
				connectionsLock.unlock();
			}
		} catch (SQLException e) {
			// Try to close the connection
			logException(LOG, WARNING, e);
			try {
				txn.close();
			} catch (SQLException e1) {
				logException(LOG, WARNING, e1);
			}
			// Whatever happens, allow the database to close
			connectionsLock.lock();
			try {
				openConnections--;
				connectionsChanged.signalAll();
			} finally {
				connectionsLock.unlock();
			}
		}
	}

	@Override
	public void commitTransaction(Connection txn) throws DbException {
		try {
			txn.commit();
		} catch (SQLException e) {
			throw new DbException(e);
		}
		connectionsLock.lock();
		try {
			connections.add(txn);
			connectionsChanged.signalAll();
		} finally {
			connectionsLock.unlock();
		}
	}

	void closeAllConnections() throws SQLException {
		boolean interrupted = false;
		connectionsLock.lock();
		try {
			closed = true;
			for (Connection c : connections) c.close();
			openConnections -= connections.size();
			connections.clear();
			while (openConnections > 0) {
				try {
					connectionsChanged.await();
				} catch (InterruptedException e) {
					LOG.warning("Interrupted while closing connections");
					interrupted = true;
				}
				for (Connection c : connections) c.close();
				openConnections -= connections.size();
				connections.clear();
			}
		} finally {
			connectionsLock.unlock();
		}

		if (interrupted) Thread.currentThread().interrupt();
	}

	@Override
	public ContactId addContact(Connection txn, Author remote, AuthorId local,
			boolean verified, boolean active) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Create a contact row
			String sql = "INSERT INTO contacts"
					+ " (authorId, formatVersion, name, publicKey,"
					+ " localAuthorId,"
					+ " verified, active)"
					+ " VALUES (?, ?, ?, ?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, remote.getId().getBytes());
			ps.setInt(2, remote.getFormatVersion());
			ps.setString(3, remote.getName());
			ps.setBytes(4, remote.getPublicKey());
			ps.setBytes(5, local.getBytes());
			ps.setBoolean(6, verified);
			ps.setBoolean(7, active);
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
			// Get the new (highest) contact ID
			sql = "SELECT contactId FROM contacts"
					+ " ORDER BY contactId DESC LIMIT 1";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			if (!rs.next()) throw new DbStateException();
			ContactId c = new ContactId(rs.getInt(1));
			if (rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return c;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void addGroup(Connection txn, Group g) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO groups"
					+ " (groupId, clientId, majorVersion, descriptor)"
					+ " VALUES (?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getId().getBytes());
			ps.setString(2, g.getClientId().getString());
			ps.setInt(3, g.getMajorVersion());
			ps.setBytes(4, g.getDescriptor());
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void addGroupVisibility(Connection txn, ContactId c, GroupId g,
			boolean groupShared) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO groupVisibilities"
					+ " (contactId, groupId, shared)"
					+ " VALUES (?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setBytes(2, g.getBytes());
			ps.setBoolean(3, groupShared);
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
			// Create a status row for each message in the group
			addStatus(txn, c, g, groupShared);
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	private void addStatus(Connection txn, ContactId c, GroupId g,
			boolean groupShared) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT messageId, timestamp, state, shared,"
					+ " length, raw IS NULL"
					+ " FROM messages"
					+ " WHERE groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			rs = ps.executeQuery();
			while (rs.next()) {
				MessageId id = new MessageId(rs.getBytes(1));
				long timestamp = rs.getLong(2);
				State state = State.fromValue(rs.getInt(3));
				boolean messageShared = rs.getBoolean(4);
				int length = rs.getInt(5);
				boolean deleted = rs.getBoolean(6);
				boolean seen = removeOfferedMessage(txn, c, id);
				addStatus(txn, id, c, g, timestamp, length, state, groupShared,
						messageShared, deleted, seen);
			}
			rs.close();
			ps.close();
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void addLocalAuthor(Connection txn, LocalAuthor a)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO localAuthors"
					+ " (authorId, formatVersion, name, publicKey,"
					+ " privateKey, created)"
					+ " VALUES (?, ?, ?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, a.getId().getBytes());
			ps.setInt(2, a.getFormatVersion());
			ps.setString(3, a.getName());
			ps.setBytes(4, a.getPublicKey());
			ps.setBytes(5, a.getPrivateKey());
			ps.setLong(6, a.getTimeCreated());
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void addMessage(Connection txn, Message m, State state,
			boolean messageShared, @Nullable ContactId sender)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO messages (messageId, groupId, timestamp,"
					+ " state, shared, length, raw)"
					+ " VALUES (?, ?, ?, ?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getId().getBytes());
			ps.setBytes(2, m.getGroupId().getBytes());
			ps.setLong(3, m.getTimestamp());
			ps.setInt(4, state.getValue());
			ps.setBoolean(5, messageShared);
			byte[] raw = m.getRaw();
			ps.setInt(6, raw.length);
			ps.setBytes(7, raw);
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
			// Create a status row for each contact that can see the group
			Map<ContactId, Boolean> visibility =
					getGroupVisibility(txn, m.getGroupId());
			for (Entry<ContactId, Boolean> e : visibility.entrySet()) {
				ContactId c = e.getKey();
				boolean offered = removeOfferedMessage(txn, c, m.getId());
				boolean seen = offered || (sender != null && c.equals(sender));
				addStatus(txn, m.getId(), c, m.getGroupId(), m.getTimestamp(),
						m.getLength(), state, e.getValue(), messageShared,
						false, seen);
			}
			// Update denormalised column in messageDependencies if dependency
			// is in same group as dependent
			sql = "UPDATE messageDependencies SET dependencyState = ?"
					+ " WHERE groupId = ? AND dependencyId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, state.getValue());
			ps.setBytes(2, m.getGroupId().getBytes());
			ps.setBytes(3, m.getId().getBytes());
			affected = ps.executeUpdate();
			if (affected < 0) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void addOfferedMessage(Connection txn, ContactId c, MessageId m)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT NULL FROM offers"
					+ " WHERE messageId = ? AND contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setInt(2, c.getInt());
			rs = ps.executeQuery();
			boolean found = rs.next();
			if (rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			if (found) return;
			sql = "INSERT INTO offers (messageId, contactId) VALUES (?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setInt(2, c.getInt());
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	private void addStatus(Connection txn, MessageId m, ContactId c, GroupId g,
			long timestamp, int length, State state, boolean groupShared,
			boolean messageShared, boolean deleted, boolean seen)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO statuses (messageId, contactId, groupId,"
					+ " timestamp, length, state, groupShared, messageShared,"
					+ " deleted, ack, seen, requested, expiry, txCount)"
					+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE, 0, 0)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setInt(2, c.getInt());
			ps.setBytes(3, g.getBytes());
			ps.setLong(4, timestamp);
			ps.setInt(5, length);
			ps.setInt(6, state.getValue());
			ps.setBoolean(7, groupShared);
			ps.setBoolean(8, messageShared);
			ps.setBoolean(9, deleted);
			ps.setBoolean(10, seen);
			ps.setBoolean(11, seen);
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void addMessageDependency(Connection txn, Message dependent,
			MessageId dependency, State dependentState) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Get state of dependency if present and in same group as dependent
			String sql = "SELECT state FROM messages"
					+ " WHERE messageId = ? AND groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, dependency.getBytes());
			ps.setBytes(2, dependent.getGroupId().getBytes());
			rs = ps.executeQuery();
			State dependencyState = null;
			if (rs.next()) {
				dependencyState = State.fromValue(rs.getInt(1));
				if (rs.next()) throw new DbStateException();
			}
			rs.close();
			ps.close();
			// Create messageDependencies row
			sql = "INSERT INTO messageDependencies"
					+ " (groupId, messageId, dependencyId, messageState,"
					+ " dependencyState)"
					+ " VALUES (?, ?, ?, ? ,?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, dependent.getGroupId().getBytes());
			ps.setBytes(2, dependent.getId().getBytes());
			ps.setBytes(3, dependency.getBytes());
			ps.setInt(4, dependentState.getValue());
			if (dependencyState == null) ps.setNull(5, INTEGER);
			else ps.setInt(5, dependencyState.getValue());
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void addTransport(Connection txn, TransportId t, int maxLatency)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO transports (transportId, maxLatency)"
					+ " VALUES (?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setString(1, t.getString());
			ps.setLong(2, maxLatency);
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public KeySetId addTransportKeys(Connection txn, ContactId c,
			TransportKeys k) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Store the outgoing keys
			String sql = "INSERT INTO outgoingKeys (contactId, transportId,"
					+ " rotationPeriod, tagKey, headerKey, stream, active)"
					+ " VALUES (?, ?, ?, ?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setString(2, k.getTransportId().getString());
			OutgoingKeys outCurr = k.getCurrentOutgoingKeys();
			ps.setLong(3, outCurr.getRotationPeriod());
			ps.setBytes(4, outCurr.getTagKey().getBytes());
			ps.setBytes(5, outCurr.getHeaderKey().getBytes());
			ps.setLong(6, outCurr.getStreamCounter());
			ps.setBoolean(7, outCurr.isActive());
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
			// Get the new (highest) key set ID
			sql = "SELECT keySetId FROM outgoingKeys"
					+ " ORDER BY keySetId DESC LIMIT 1";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			if (!rs.next()) throw new DbStateException();
			KeySetId keySetId = new KeySetId(rs.getInt(1));
			if (rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			// Store the incoming keys
			sql = "INSERT INTO incomingKeys (keySetId, contactId, transportId,"
					+ " rotationPeriod, tagKey, headerKey, base, bitmap,"
					+ " periodOffset)"
					+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, keySetId.getInt());
			ps.setInt(2, c.getInt());
			ps.setString(3, k.getTransportId().getString());
			// Previous rotation period
			IncomingKeys inPrev = k.getPreviousIncomingKeys();
			ps.setLong(4, inPrev.getRotationPeriod());
			ps.setBytes(5, inPrev.getTagKey().getBytes());
			ps.setBytes(6, inPrev.getHeaderKey().getBytes());
			ps.setLong(7, inPrev.getWindowBase());
			ps.setBytes(8, inPrev.getWindowBitmap());
			ps.setInt(9, OFFSET_PREV);
			ps.addBatch();
			// Current rotation period
			IncomingKeys inCurr = k.getCurrentIncomingKeys();
			ps.setLong(4, inCurr.getRotationPeriod());
			ps.setBytes(5, inCurr.getTagKey().getBytes());
			ps.setBytes(6, inCurr.getHeaderKey().getBytes());
			ps.setLong(7, inCurr.getWindowBase());
			ps.setBytes(8, inCurr.getWindowBitmap());
			ps.setInt(9, OFFSET_CURR);
			ps.addBatch();
			// Next rotation period
			IncomingKeys inNext = k.getNextIncomingKeys();
			ps.setLong(4, inNext.getRotationPeriod());
			ps.setBytes(5, inNext.getTagKey().getBytes());
			ps.setBytes(6, inNext.getHeaderKey().getBytes());
			ps.setLong(7, inNext.getWindowBase());
			ps.setBytes(8, inNext.getWindowBitmap());
			ps.setInt(9, OFFSET_NEXT);
			ps.addBatch();
			int[] batchAffected = ps.executeBatch();
			if (batchAffected.length != 3) throw new DbStateException();
			for (int rows : batchAffected)
				if (rows != 1) throw new DbStateException();
			ps.close();
			return keySetId;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public boolean containsContact(Connection txn, AuthorId remote,
			AuthorId local) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT NULL FROM contacts"
					+ " WHERE authorId = ? AND localAuthorId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, remote.getBytes());
			ps.setBytes(2, local.getBytes());
			rs = ps.executeQuery();
			boolean found = rs.next();
			if (rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return found;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public boolean containsContact(Connection txn, ContactId c)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT NULL FROM contacts WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			boolean found = rs.next();
			if (rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return found;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public boolean containsGroup(Connection txn, GroupId g)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT NULL FROM groups WHERE groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			rs = ps.executeQuery();
			boolean found = rs.next();
			if (rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return found;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public boolean containsLocalAuthor(Connection txn, AuthorId a)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT NULL FROM localAuthors WHERE authorId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, a.getBytes());
			rs = ps.executeQuery();
			boolean found = rs.next();
			if (rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return found;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public boolean containsMessage(Connection txn, MessageId m)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT NULL FROM messages WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			rs = ps.executeQuery();
			boolean found = rs.next();
			if (rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return found;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public boolean containsTransport(Connection txn, TransportId t)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT NULL FROM transports WHERE transportId = ?";
			ps = txn.prepareStatement(sql);
			ps.setString(1, t.getString());
			rs = ps.executeQuery();
			boolean found = rs.next();
			if (rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return found;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public boolean containsVisibleMessage(Connection txn, ContactId c,
			MessageId m) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT NULL FROM statuses"
					+ " WHERE messageId = ? AND contactId = ?"
					+ " AND messageShared = TRUE";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setInt(2, c.getInt());
			rs = ps.executeQuery();
			boolean found = rs.next();
			if (rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return found;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public int countOfferedMessages(Connection txn, ContactId c)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT COUNT (messageId) FROM offers "
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			if (!rs.next()) throw new DbException();
			int count = rs.getInt(1);
			if (rs.next()) throw new DbException();
			rs.close();
			ps.close();
			return count;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void deleteMessage(Connection txn, MessageId m) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE messages SET raw = NULL WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			int affected = ps.executeUpdate();
			if (affected < 0) throw new DbStateException();
			if (affected > 1) throw new DbStateException();
			ps.close();
			// Update denormalised column in statuses
			sql = "UPDATE statuses SET deleted = TRUE WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			affected = ps.executeUpdate();
			if (affected < 0) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void deleteMessageMetadata(Connection txn, MessageId m)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "DELETE FROM messageMetadata WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			int affected = ps.executeUpdate();
			if (affected < 0) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public Contact getContact(Connection txn, ContactId c) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT authorId, formatVersion, name, publicKey,"
					+ " localAuthorId, verified, active"
					+ " FROM contacts"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			if (!rs.next()) throw new DbStateException();
			AuthorId authorId = new AuthorId(rs.getBytes(1));
			int formatVersion = rs.getInt(2);
			String name = rs.getString(3);
			byte[] publicKey = rs.getBytes(4);
			AuthorId localAuthorId = new AuthorId(rs.getBytes(5));
			boolean verified = rs.getBoolean(6);
			boolean active = rs.getBoolean(7);
			rs.close();
			ps.close();
			Author author =
					new Author(authorId, formatVersion, name, publicKey);
			return new Contact(c, author, localAuthorId, verified, active);
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public Collection<Contact> getContacts(Connection txn)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT contactId, authorId, formatVersion, name,"
					+ " publicKey, localAuthorId, verified, active"
					+ " FROM contacts";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			List<Contact> contacts = new ArrayList<>();
			while (rs.next()) {
				ContactId contactId = new ContactId(rs.getInt(1));
				AuthorId authorId = new AuthorId(rs.getBytes(2));
				int formatVersion = rs.getInt(3);
				String name = rs.getString(4);
				byte[] publicKey = rs.getBytes(5);
				Author author =
						new Author(authorId, formatVersion, name, publicKey);
				AuthorId localAuthorId = new AuthorId(rs.getBytes(6));
				boolean verified = rs.getBoolean(7);
				boolean active = rs.getBoolean(8);
				contacts.add(new Contact(contactId, author, localAuthorId,
						verified, active));
			}
			rs.close();
			ps.close();
			return contacts;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public Collection<ContactId> getContacts(Connection txn, AuthorId local)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT contactId FROM contacts"
					+ " WHERE localAuthorId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, local.getBytes());
			rs = ps.executeQuery();
			List<ContactId> ids = new ArrayList<>();
			while (rs.next()) ids.add(new ContactId(rs.getInt(1)));
			rs.close();
			ps.close();
			return ids;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public Collection<Contact> getContactsByAuthorId(Connection txn,
			AuthorId remote) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT contactId, formatVersion, name, publicKey,"
					+ " localAuthorId, verified, active"
					+ " FROM contacts"
					+ " WHERE authorId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, remote.getBytes());
			rs = ps.executeQuery();
			List<Contact> contacts = new ArrayList<>();
			while (rs.next()) {
				ContactId c = new ContactId(rs.getInt(1));
				int formatVersion = rs.getInt(2);
				String name = rs.getString(3);
				byte[] publicKey = rs.getBytes(4);
				AuthorId localAuthorId = new AuthorId(rs.getBytes(5));
				boolean verified = rs.getBoolean(6);
				boolean active = rs.getBoolean(7);
				Author author =
						new Author(remote, formatVersion, name, publicKey);
				contacts.add(new Contact(c, author, localAuthorId, verified,
						active));
			}
			rs.close();
			ps.close();
			return contacts;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public Group getGroup(Connection txn, GroupId g) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT clientId, majorVersion, descriptor"
					+ " FROM groups WHERE groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			rs = ps.executeQuery();
			if (!rs.next()) throw new DbStateException();
			ClientId clientId = new ClientId(rs.getString(1));
			int majorVersion = rs.getInt(2);
			byte[] descriptor = rs.getBytes(3);
			rs.close();
			ps.close();
			return new Group(g, clientId, majorVersion, descriptor);
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public Collection<Group> getGroups(Connection txn, ClientId c,
			int majorVersion) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT groupId, descriptor FROM groups"
					+ " WHERE clientId = ? AND majorVersion = ?";
			ps = txn.prepareStatement(sql);
			ps.setString(1, c.getString());
			ps.setInt(2, majorVersion);
			rs = ps.executeQuery();
			List<Group> groups = new ArrayList<>();
			while (rs.next()) {
				GroupId id = new GroupId(rs.getBytes(1));
				byte[] descriptor = rs.getBytes(2);
				groups.add(new Group(id, c, majorVersion, descriptor));
			}
			rs.close();
			ps.close();
			return groups;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public Visibility getGroupVisibility(Connection txn, ContactId c, GroupId g)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT shared FROM groupVisibilities"
					+ " WHERE contactId = ? AND groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setBytes(2, g.getBytes());
			rs = ps.executeQuery();
			Visibility v;
			if (rs.next()) v = rs.getBoolean(1) ? SHARED : VISIBLE;
			else v = INVISIBLE;
			if (rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return v;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public Map<ContactId, Boolean> getGroupVisibility(Connection txn, GroupId g)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT contactId, shared FROM groupVisibilities"
					+ " WHERE groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			rs = ps.executeQuery();
			Map<ContactId, Boolean> visible = new HashMap<>();
			while (rs.next())
				visible.put(new ContactId(rs.getInt(1)), rs.getBoolean(2));
			rs.close();
			ps.close();
			return visible;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public LocalAuthor getLocalAuthor(Connection txn, AuthorId a)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT formatVersion, name, publicKey,"
					+ " privateKey, created"
					+ " FROM localAuthors"
					+ " WHERE authorId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, a.getBytes());
			rs = ps.executeQuery();
			if (!rs.next()) throw new DbStateException();
			int formatVersion = rs.getInt(1);
			String name = rs.getString(2);
			byte[] publicKey = rs.getBytes(3);
			byte[] privateKey = rs.getBytes(4);
			long created = rs.getLong(5);
			LocalAuthor localAuthor = new LocalAuthor(a, formatVersion, name,
					publicKey, privateKey, created);
			if (rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return localAuthor;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public Collection<LocalAuthor> getLocalAuthors(Connection txn)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT authorId, formatVersion, name, publicKey,"
					+ " privateKey, created"
					+ " FROM localAuthors";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			List<LocalAuthor> authors = new ArrayList<>();
			while (rs.next()) {
				AuthorId authorId = new AuthorId(rs.getBytes(1));
				int formatVersion = rs.getInt(2);
				String name = rs.getString(3);
				byte[] publicKey = rs.getBytes(4);
				byte[] privateKey = rs.getBytes(5);
				long created = rs.getLong(6);
				authors.add(new LocalAuthor(authorId, formatVersion, name,
						publicKey, privateKey, created));
			}
			rs.close();
			ps.close();
			return authors;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public Collection<MessageId> getMessageIds(Connection txn, GroupId g)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT messageId FROM messages"
					+ " WHERE groupId = ? AND state = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			ps.setInt(2, DELIVERED.getValue());
			rs = ps.executeQuery();
			List<MessageId> ids = new ArrayList<>();
			while (rs.next()) ids.add(new MessageId(rs.getBytes(1)));
			rs.close();
			ps.close();
			return ids;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public Collection<MessageId> getMessageIds(Connection txn, GroupId g,
			Metadata query) throws DbException {
		// If there are no query terms, return all delivered messages
		if (query.isEmpty()) return getMessageIds(txn, g);
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Retrieve the message IDs for each query term and intersect
			Set<MessageId> intersection = null;
			String sql = "SELECT messageId FROM messageMetadata"
					+ " WHERE groupId = ? AND state = ?"
					+ " AND metaKey = ? AND value = ?";
			for (Entry<String, byte[]> e : query.entrySet()) {
				ps = txn.prepareStatement(sql);
				ps.setBytes(1, g.getBytes());
				ps.setInt(2, DELIVERED.getValue());
				ps.setString(3, e.getKey());
				ps.setBytes(4, e.getValue());
				rs = ps.executeQuery();
				Set<MessageId> ids = new HashSet<>();
				while (rs.next()) ids.add(new MessageId(rs.getBytes(1)));
				rs.close();
				ps.close();
				if (intersection == null) intersection = ids;
				else intersection.retainAll(ids);
				// Return early if there are no matches
				if (intersection.isEmpty()) return Collections.emptySet();
			}
			if (intersection == null) throw new AssertionError();
			return intersection;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public Map<MessageId, Metadata> getMessageMetadata(Connection txn,
			GroupId g) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT messageId, metaKey, value"
					+ " FROM messageMetadata"
					+ " WHERE groupId = ? AND state = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			ps.setInt(2, DELIVERED.getValue());
			rs = ps.executeQuery();
			Map<MessageId, Metadata> all = new HashMap<>();
			while (rs.next()) {
				MessageId messageId = new MessageId(rs.getBytes(1));
				Metadata metadata = all.get(messageId);
				if (metadata == null) {
					metadata = new Metadata();
					all.put(messageId, metadata);
				}
				metadata.put(rs.getString(2), rs.getBytes(3));
			}
			rs.close();
			ps.close();
			return all;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public Map<MessageId, Metadata> getMessageMetadata(Connection txn,
			GroupId g, Metadata query) throws DbException {
		// Retrieve the matching message IDs
		Collection<MessageId> matches = getMessageIds(txn, g, query);
		if (matches.isEmpty()) return Collections.emptyMap();
		// Retrieve the metadata for each match
		Map<MessageId, Metadata> all = new HashMap<>(matches.size());
		for (MessageId m : matches) all.put(m, getMessageMetadata(txn, m));
		return all;
	}

	@Override
	public Metadata getGroupMetadata(Connection txn, GroupId g)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT metaKey, value FROM groupMetadata"
					+ " WHERE groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			rs = ps.executeQuery();
			Metadata metadata = new Metadata();
			while (rs.next()) metadata.put(rs.getString(1), rs.getBytes(2));
			rs.close();
			ps.close();
			return metadata;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public Metadata getMessageMetadata(Connection txn, MessageId m)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT metaKey, value FROM messageMetadata"
					+ " WHERE state = ? AND messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, DELIVERED.getValue());
			ps.setBytes(2, m.getBytes());
			rs = ps.executeQuery();
			Metadata metadata = new Metadata();
			while (rs.next()) metadata.put(rs.getString(1), rs.getBytes(2));
			rs.close();
			ps.close();
			return metadata;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public Metadata getMessageMetadataForValidator(Connection txn, MessageId m)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT metaKey, value FROM messageMetadata"
					+ " WHERE (state = ? OR state = ?)"
					+ " AND messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, DELIVERED.getValue());
			ps.setInt(2, PENDING.getValue());
			ps.setBytes(3, m.getBytes());
			rs = ps.executeQuery();
			Metadata metadata = new Metadata();
			while (rs.next()) metadata.put(rs.getString(1), rs.getBytes(2));
			rs.close();
			ps.close();
			return metadata;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public Collection<MessageStatus> getMessageStatus(Connection txn,
			ContactId c, GroupId g) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT messageId, txCount > 0, seen FROM statuses"
					+ " WHERE groupId = ? AND contactId = ? AND state = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			ps.setInt(2, c.getInt());
			ps.setInt(3, DELIVERED.getValue());
			rs = ps.executeQuery();
			List<MessageStatus> statuses = new ArrayList<>();
			while (rs.next()) {
				MessageId messageId = new MessageId(rs.getBytes(1));
				boolean sent = rs.getBoolean(2);
				boolean seen = rs.getBoolean(3);
				statuses.add(new MessageStatus(messageId, c, sent, seen));
			}
			rs.close();
			ps.close();
			return statuses;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	@Nullable
	public MessageStatus getMessageStatus(Connection txn, ContactId c,
			MessageId m) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT txCount > 0, seen FROM statuses"
					+ " WHERE messageId = ? AND contactId = ? AND state = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setInt(2, c.getInt());
			ps.setInt(3, DELIVERED.getValue());
			rs = ps.executeQuery();
			MessageStatus status = null;
			if (rs.next()) {
				boolean sent = rs.getBoolean(1);
				boolean seen = rs.getBoolean(2);
				status = new MessageStatus(m, c, sent, seen);
			}
			if (rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return status;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public Map<MessageId, State> getMessageDependencies(Connection txn,
			MessageId m) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT dependencyId, dependencyState"
					+ " FROM messageDependencies"
					+ " WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			rs = ps.executeQuery();
			Map<MessageId, State> dependencies = new HashMap<>();
			while (rs.next()) {
				MessageId dependency = new MessageId(rs.getBytes(1));
				State state = State.fromValue(rs.getInt(2));
				if (rs.wasNull())
					state = UNKNOWN; // Missing or in a different group
				dependencies.put(dependency, state);
			}
			rs.close();
			ps.close();
			return dependencies;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public Map<MessageId, State> getMessageDependents(Connection txn,
			MessageId m) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Exclude dependencies that are missing or in a different group
			// from the dependent
			String sql = "SELECT messageId, messageState"
					+ " FROM messageDependencies"
					+ " WHERE dependencyId = ?"
					+ " AND dependencyState IS NOT NULL";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			rs = ps.executeQuery();
			Map<MessageId, State> dependents = new HashMap<>();
			while (rs.next()) {
				MessageId dependent = new MessageId(rs.getBytes(1));
				State state = State.fromValue(rs.getInt(2));
				dependents.put(dependent, state);
			}
			rs.close();
			ps.close();
			return dependents;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public State getMessageState(Connection txn, MessageId m)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT state FROM messages WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			rs = ps.executeQuery();
			if (!rs.next()) throw new DbStateException();
			State state = State.fromValue(rs.getInt(1));
			if (rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return state;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public Collection<MessageId> getMessagesToAck(Connection txn, ContactId c,
			int maxMessages) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT messageId FROM statuses"
					+ " WHERE contactId = ? AND ack = TRUE"
					+ " LIMIT ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setInt(2, maxMessages);
			rs = ps.executeQuery();
			List<MessageId> ids = new ArrayList<>();
			while (rs.next()) ids.add(new MessageId(rs.getBytes(1)));
			rs.close();
			ps.close();
			return ids;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public Collection<MessageId> getMessagesToOffer(Connection txn,
			ContactId c, int maxMessages) throws DbException {
		long now = clock.currentTimeMillis();
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT messageId FROM statuses"
					+ " WHERE contactId = ? AND state = ?"
					+ " AND groupShared = TRUE AND messageShared = TRUE"
					+ " AND deleted = FALSE"
					+ " AND seen = FALSE AND requested = FALSE"
					+ " AND expiry < ?"
					+ " ORDER BY timestamp LIMIT ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setInt(2, DELIVERED.getValue());
			ps.setLong(3, now);
			ps.setInt(4, maxMessages);
			rs = ps.executeQuery();
			List<MessageId> ids = new ArrayList<>();
			while (rs.next()) ids.add(new MessageId(rs.getBytes(1)));
			rs.close();
			ps.close();
			return ids;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public Collection<MessageId> getMessagesToRequest(Connection txn,
			ContactId c, int maxMessages) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT messageId FROM offers"
					+ " WHERE contactId = ?"
					+ " LIMIT ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setInt(2, maxMessages);
			rs = ps.executeQuery();
			List<MessageId> ids = new ArrayList<>();
			while (rs.next()) ids.add(new MessageId(rs.getBytes(1)));
			rs.close();
			ps.close();
			return ids;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public Collection<MessageId> getMessagesToSend(Connection txn, ContactId c,
			int maxLength) throws DbException {
		long now = clock.currentTimeMillis();
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT length, messageId FROM statuses"
					+ " WHERE contactId = ? AND state = ?"
					+ " AND groupShared = TRUE AND messageShared = TRUE"
					+ " AND deleted = FALSE"
					+ " AND seen = FALSE"
					+ " AND expiry < ?"
					+ " ORDER BY timestamp";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setInt(2, DELIVERED.getValue());
			ps.setLong(3, now);
			rs = ps.executeQuery();
			List<MessageId> ids = new ArrayList<>();
			int total = 0;
			while (rs.next()) {
				int length = rs.getInt(1);
				if (total + length > maxLength) break;
				ids.add(new MessageId(rs.getBytes(2)));
				total += length;
			}
			rs.close();
			ps.close();
			return ids;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public Collection<MessageId> getMessagesToValidate(Connection txn)
			throws DbException {
		return getMessagesInState(txn, UNKNOWN);
	}

	@Override
	public Collection<MessageId> getPendingMessages(Connection txn)
			throws DbException {
		return getMessagesInState(txn, PENDING);
	}

	private Collection<MessageId> getMessagesInState(Connection txn,
			State state) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT messageId FROM messages"
					+ " WHERE state = ? AND raw IS NOT NULL";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, state.getValue());
			rs = ps.executeQuery();
			List<MessageId> ids = new ArrayList<>();
			while (rs.next()) ids.add(new MessageId(rs.getBytes(1)));
			rs.close();
			ps.close();
			return ids;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public Collection<MessageId> getMessagesToShare(Connection txn)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT m.messageId FROM messages AS m"
					+ " JOIN messageDependencies AS d"
					+ " ON m.messageId = d.dependencyId"
					+ " JOIN messages AS m1"
					+ " ON d.messageId = m1.messageId"
					+ " WHERE m.state = ?"
					+ " AND m.shared = FALSE AND m1.shared = TRUE";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, DELIVERED.getValue());
			rs = ps.executeQuery();
			List<MessageId> ids = new ArrayList<>();
			while (rs.next()) ids.add(new MessageId(rs.getBytes(1)));
			rs.close();
			ps.close();
			return ids;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public long getNextSendTime(Connection txn, ContactId c)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT expiry FROM statuses"
					+ " WHERE contactId = ? AND state = ?"
					+ " AND groupShared = TRUE AND messageShared = TRUE"
					+ " AND deleted = FALSE AND seen = FALSE"
					+ " ORDER BY expiry LIMIT 1";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setInt(2, DELIVERED.getValue());
			rs = ps.executeQuery();
			long nextSendTime = Long.MAX_VALUE;
			if (rs.next()) {
				nextSendTime = rs.getLong(1);
				if (rs.next()) throw new AssertionError();
			}
			rs.close();
			ps.close();
			return nextSendTime;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	@Nullable
	public byte[] getRawMessage(Connection txn, MessageId m)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT raw FROM messages WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			rs = ps.executeQuery();
			if (!rs.next()) throw new DbStateException();
			byte[] raw = rs.getBytes(1);
			if (rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return raw;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public Collection<MessageId> getRequestedMessagesToSend(Connection txn,
			ContactId c, int maxLength) throws DbException {
		long now = clock.currentTimeMillis();
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT length, messageId FROM statuses"
					+ " WHERE contactId = ? AND state = ?"
					+ " AND groupShared = TRUE AND messageShared = TRUE"
					+ " AND deleted = FALSE"
					+ " AND seen = FALSE AND requested = TRUE"
					+ " AND expiry < ?"
					+ " ORDER BY timestamp";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setInt(2, DELIVERED.getValue());
			ps.setLong(3, now);
			rs = ps.executeQuery();
			List<MessageId> ids = new ArrayList<>();
			int total = 0;
			while (rs.next()) {
				int length = rs.getInt(1);
				if (total + length > maxLength) break;
				ids.add(new MessageId(rs.getBytes(2)));
				total += length;
			}
			rs.close();
			ps.close();
			return ids;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public Settings getSettings(Connection txn, String namespace)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT settingKey, value FROM settings"
					+ " WHERE namespace = ?";
			ps = txn.prepareStatement(sql);
			ps.setString(1, namespace);
			rs = ps.executeQuery();
			Settings s = new Settings();
			while (rs.next()) s.put(rs.getString(1), rs.getString(2));
			rs.close();
			ps.close();
			return s;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public Collection<KeySet> getTransportKeys(Connection txn, TransportId t)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Retrieve the incoming keys
			String sql = "SELECT rotationPeriod, tagKey, headerKey,"
					+ " base, bitmap"
					+ " FROM incomingKeys"
					+ " WHERE transportId = ?"
					+ " ORDER BY keySetId, periodOffset";
			ps = txn.prepareStatement(sql);
			ps.setString(1, t.getString());
			rs = ps.executeQuery();
			List<IncomingKeys> inKeys = new ArrayList<>();
			while (rs.next()) {
				long rotationPeriod = rs.getLong(1);
				SecretKey tagKey = new SecretKey(rs.getBytes(2));
				SecretKey headerKey = new SecretKey(rs.getBytes(3));
				long windowBase = rs.getLong(4);
				byte[] windowBitmap = rs.getBytes(5);
				inKeys.add(new IncomingKeys(tagKey, headerKey, rotationPeriod,
						windowBase, windowBitmap));
			}
			rs.close();
			ps.close();
			// Retrieve the outgoing keys in the same order
			sql = "SELECT keySetId, contactId, rotationPeriod,"
					+ " tagKey, headerKey, stream, active"
					+ " FROM outgoingKeys"
					+ " WHERE transportId = ?"
					+ " ORDER BY keySetId";
			ps = txn.prepareStatement(sql);
			ps.setString(1, t.getString());
			rs = ps.executeQuery();
			Collection<KeySet> keys = new ArrayList<>();
			for (int i = 0; rs.next(); i++) {
				// There should be three times as many incoming keys
				if (inKeys.size() < (i + 1) * 3) throw new DbStateException();
				KeySetId keySetId = new KeySetId(rs.getInt(1));
				ContactId contactId = new ContactId(rs.getInt(2));
				long rotationPeriod = rs.getLong(3);
				SecretKey tagKey = new SecretKey(rs.getBytes(4));
				SecretKey headerKey = new SecretKey(rs.getBytes(5));
				long streamCounter = rs.getLong(6);
				boolean active = rs.getBoolean(7);
				OutgoingKeys outCurr = new OutgoingKeys(tagKey, headerKey,
						rotationPeriod, streamCounter, active);
				IncomingKeys inPrev = inKeys.get(i * 3);
				IncomingKeys inCurr = inKeys.get(i * 3 + 1);
				IncomingKeys inNext = inKeys.get(i * 3 + 2);
				TransportKeys transportKeys = new TransportKeys(t, inPrev,
						inCurr, inNext, outCurr);
				keys.add(new KeySet(keySetId, contactId, transportKeys));
			}
			rs.close();
			ps.close();
			return keys;
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void incrementStreamCounter(Connection txn, TransportId t,
			KeySetId k) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE outgoingKeys SET stream = stream + 1"
					+ " WHERE transportId = ? AND keySetId = ?";
			ps = txn.prepareStatement(sql);
			ps.setString(1, t.getString());
			ps.setInt(2, k.getInt());
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void lowerAckFlag(Connection txn, ContactId c,
			Collection<MessageId> acked) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE statuses SET ack = FALSE"
					+ " WHERE messageId = ? AND contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(2, c.getInt());
			for (MessageId m : acked) {
				ps.setBytes(1, m.getBytes());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if (batchAffected.length != acked.size())
				throw new DbStateException();
			for (int rows : batchAffected) {
				if (rows < 0) throw new DbStateException();
				if (rows > 1) throw new DbStateException();
			}
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void lowerRequestedFlag(Connection txn, ContactId c,
			Collection<MessageId> requested) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE statuses SET requested = FALSE"
					+ " WHERE messageId = ? AND contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(2, c.getInt());
			for (MessageId m : requested) {
				ps.setBytes(1, m.getBytes());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if (batchAffected.length != requested.size())
				throw new DbStateException();
			for (int rows : batchAffected) {
				if (rows < 0) throw new DbStateException();
				if (rows > 1) throw new DbStateException();
			}
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void mergeGroupMetadata(Connection txn, GroupId g, Metadata meta)
			throws DbException {
		PreparedStatement ps = null;
		try {
			Map<String, byte[]> added = removeOrUpdateMetadata(txn,
					g.getBytes(), meta, "groupMetadata", "groupId");
			if (added.isEmpty()) return;
			// Insert any keys that don't already exist
			String sql = "INSERT INTO groupMetadata (groupId, metaKey, value)"
					+ " VALUES (?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			for (Entry<String, byte[]> e : added.entrySet()) {
				ps.setString(2, e.getKey());
				ps.setBytes(3, e.getValue());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if (batchAffected.length != added.size())
				throw new DbStateException();
			for (int rows : batchAffected)
				if (rows != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void mergeMessageMetadata(Connection txn, MessageId m,
			Metadata meta) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			Map<String, byte[]> added = removeOrUpdateMetadata(txn,
					m.getBytes(), meta, "messageMetadata", "messageId");
			if (added.isEmpty()) return;
			// Get the group ID and message state for the denormalised columns
			String sql = "SELECT groupId, state FROM messages"
					+ " WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			rs = ps.executeQuery();
			if (!rs.next()) throw new DbStateException();
			GroupId g = new GroupId(rs.getBytes(1));
			State state = State.fromValue(rs.getInt(2));
			rs.close();
			ps.close();
			// Insert any keys that don't already exist
			sql = "INSERT INTO messageMetadata"
					+ " (messageId, groupId, state, metaKey, value)"
					+ " VALUES (?, ?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setBytes(2, g.getBytes());
			ps.setInt(3, state.getValue());
			for (Entry<String, byte[]> e : added.entrySet()) {
				ps.setString(4, e.getKey());
				ps.setBytes(5, e.getValue());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if (batchAffected.length != added.size())
				throw new DbStateException();
			for (int rows : batchAffected)
				if (rows != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	// Removes or updates any existing entries, returns any entries that
	// need to be added
	private Map<String, byte[]> removeOrUpdateMetadata(Connection txn,
			byte[] id, Metadata meta, String tableName, String columnName)
			throws DbException {
		PreparedStatement ps = null;
		try {
			// Determine which keys are being removed
			List<String> removed = new ArrayList<>();
			Map<String, byte[]> notRemoved = new HashMap<>();
			for (Entry<String, byte[]> e : meta.entrySet()) {
				if (e.getValue() == REMOVE) removed.add(e.getKey());
				else notRemoved.put(e.getKey(), e.getValue());
			}
			// Delete any keys that are being removed
			if (!removed.isEmpty()) {
				String sql = "DELETE FROM " + tableName
						+ " WHERE " + columnName + " = ? AND metaKey = ?";
				ps = txn.prepareStatement(sql);
				ps.setBytes(1, id);
				for (String key : removed) {
					ps.setString(2, key);
					ps.addBatch();
				}
				int[] batchAffected = ps.executeBatch();
				if (batchAffected.length != removed.size())
					throw new DbStateException();
				for (int rows : batchAffected) {
					if (rows < 0) throw new DbStateException();
					if (rows > 1) throw new DbStateException();
				}
				ps.close();
			}
			if (notRemoved.isEmpty()) return Collections.emptyMap();
			// Update any keys that already exist
			String sql = "UPDATE " + tableName + " SET value = ?"
					+ " WHERE " + columnName + " = ? AND metaKey = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(2, id);
			for (Entry<String, byte[]> e : notRemoved.entrySet()) {
				ps.setBytes(1, e.getValue());
				ps.setString(3, e.getKey());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if (batchAffected.length != notRemoved.size())
				throw new DbStateException();
			for (int rows : batchAffected) {
				if (rows < 0) throw new DbStateException();
				if (rows > 1) throw new DbStateException();
			}
			ps.close();
			// Are there any keys that don't already exist?
			Map<String, byte[]> added = new HashMap<>();
			int updateIndex = 0;
			for (Entry<String, byte[]> e : notRemoved.entrySet()) {
				if (batchAffected[updateIndex++] == 0)
					added.put(e.getKey(), e.getValue());
			}
			return added;
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void mergeSettings(Connection txn, Settings s, String namespace)
			throws DbException {
		PreparedStatement ps = null;
		try {
			// Update any settings that already exist
			String sql = "UPDATE settings SET value = ?"
					+ " WHERE namespace = ? AND settingKey = ?";
			ps = txn.prepareStatement(sql);
			for (Entry<String, String> e : s.entrySet()) {
				ps.setString(1, e.getValue());
				ps.setString(2, namespace);
				ps.setString(3, e.getKey());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if (batchAffected.length != s.size()) throw new DbStateException();
			for (int rows : batchAffected) {
				if (rows < 0) throw new DbStateException();
				if (rows > 1) throw new DbStateException();
			}
			// Insert any settings that don't already exist
			sql = "INSERT INTO settings (namespace, settingKey, value)"
					+ " VALUES (?, ?, ?)";
			ps = txn.prepareStatement(sql);
			int updateIndex = 0, inserted = 0;
			for (Entry<String, String> e : s.entrySet()) {
				if (batchAffected[updateIndex] == 0) {
					ps.setString(1, namespace);
					ps.setString(2, e.getKey());
					ps.setString(3, e.getValue());
					ps.addBatch();
					inserted++;
				}
				updateIndex++;
			}
			batchAffected = ps.executeBatch();
			if (batchAffected.length != inserted) throw new DbStateException();
			for (int rows : batchAffected)
				if (rows != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void raiseAckFlag(Connection txn, ContactId c, MessageId m)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE statuses SET ack = TRUE"
					+ " WHERE messageId = ? AND contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setInt(2, c.getInt());
			int affected = ps.executeUpdate();
			if (affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void raiseRequestedFlag(Connection txn, ContactId c, MessageId m)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE statuses SET requested = TRUE"
					+ " WHERE messageId = ? AND contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setInt(2, c.getInt());
			int affected = ps.executeUpdate();
			if (affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void raiseSeenFlag(Connection txn, ContactId c, MessageId m)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE statuses SET seen = TRUE"
					+ " WHERE messageId = ? AND contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setInt(2, c.getInt());
			int affected = ps.executeUpdate();
			if (affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void removeContact(Connection txn, ContactId c)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "DELETE FROM contacts WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void removeGroup(Connection txn, GroupId g) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "DELETE FROM groups WHERE groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void removeGroupVisibility(Connection txn, ContactId c, GroupId g)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "DELETE FROM groupVisibilities"
					+ " WHERE contactId = ? AND groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setBytes(2, g.getBytes());
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
			// Remove status rows for the messages in the group
			sql = "DELETE FROM statuses"
					+ " WHERE contactId = ? AND groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setBytes(2, g.getBytes());
			affected = ps.executeUpdate();
			if (affected < 0) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void removeLocalAuthor(Connection txn, AuthorId a)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "DELETE FROM localAuthors WHERE authorId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, a.getBytes());
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void removeMessage(Connection txn, MessageId m) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "DELETE FROM messages WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	private boolean removeOfferedMessage(Connection txn, ContactId c,
			MessageId m) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "DELETE FROM offers"
					+ " WHERE contactId = ? AND messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setBytes(2, m.getBytes());
			int affected = ps.executeUpdate();
			if (affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
			return affected == 1;
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void removeOfferedMessages(Connection txn, ContactId c,
			Collection<MessageId> requested) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "DELETE FROM offers"
					+ " WHERE contactId = ? AND messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			for (MessageId m : requested) {
				ps.setBytes(2, m.getBytes());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if (batchAffected.length != requested.size())
				throw new DbStateException();
			for (int rows : batchAffected)
				if (rows != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void removeTransport(Connection txn, TransportId t)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "DELETE FROM transports WHERE transportId = ?";
			ps = txn.prepareStatement(sql);
			ps.setString(1, t.getString());
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void removeTransportKeys(Connection txn, TransportId t, KeySetId k)
			throws DbException {
		PreparedStatement ps = null;
		try {
			// Delete any existing outgoing keys - this will also remove any
			// incoming keys with the same key set ID
			String sql = "DELETE FROM outgoingKeys"
					+ " WHERE transportId = ? AND keySetId = ?";
			ps = txn.prepareStatement(sql);
			ps.setString(1, t.getString());
			ps.setInt(2, k.getInt());
			int affected = ps.executeUpdate();
			if (affected < 0) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void resetExpiryTime(Connection txn, ContactId c, MessageId m)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE statuses SET expiry = 0, txCount = 0"
					+ " WHERE messageId = ? AND contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setInt(2, c.getInt());
			int affected = ps.executeUpdate();
			if (affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void setContactVerified(Connection txn, ContactId c)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE contacts SET verified = ? WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBoolean(1, true);
			ps.setInt(2, c.getInt());
			int affected = ps.executeUpdate();
			if (affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void setContactActive(Connection txn, ContactId c, boolean active)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE contacts SET active = ? WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBoolean(1, active);
			ps.setInt(2, c.getInt());
			int affected = ps.executeUpdate();
			if (affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void setGroupVisibility(Connection txn, ContactId c, GroupId g,
			boolean shared) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE groupVisibilities SET shared = ?"
					+ " WHERE contactId = ? AND groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBoolean(1, shared);
			ps.setInt(2, c.getInt());
			ps.setBytes(3, g.getBytes());
			int affected = ps.executeUpdate();
			if (affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
			// Update denormalised column in statuses
			sql = "UPDATE statuses SET groupShared = ?"
					+ " WHERE contactId = ? AND groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBoolean(1, shared);
			ps.setInt(2, c.getInt());
			ps.setBytes(3, g.getBytes());
			affected = ps.executeUpdate();
			if (affected < 0) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void setMessageShared(Connection txn, MessageId m)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE messages SET shared = TRUE"
					+ " WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			int affected = ps.executeUpdate();
			if (affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
			// Update denormalised column in statuses
			sql = "UPDATE statuses SET messageShared = TRUE"
					+ " WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			affected = ps.executeUpdate();
			if (affected < 0) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void setMessageState(Connection txn, MessageId m, State state)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE messages SET state = ? WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, state.getValue());
			ps.setBytes(2, m.getBytes());
			int affected = ps.executeUpdate();
			if (affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
			// Update denormalised column in messageMetadata
			sql = "UPDATE messageMetadata SET state = ? WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, state.getValue());
			ps.setBytes(2, m.getBytes());
			affected = ps.executeUpdate();
			if (affected < 0) throw new DbStateException();
			ps.close();
			// Update denormalised column in statuses
			sql = "UPDATE statuses SET state = ? WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, state.getValue());
			ps.setBytes(2, m.getBytes());
			affected = ps.executeUpdate();
			if (affected < 0) throw new DbStateException();
			ps.close();
			// Update denormalised column in messageDependencies
			sql = "UPDATE messageDependencies SET messageState = ?"
					+ " WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, state.getValue());
			ps.setBytes(2, m.getBytes());
			affected = ps.executeUpdate();
			if (affected < 0) throw new DbStateException();
			ps.close();
			// Update denormalised column in messageDependencies if dependency
			// is present and in same group as dependent
			sql = "UPDATE messageDependencies SET dependencyState = ?"
					+ " WHERE dependencyId = ? AND dependencyState IS NOT NULL";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, state.getValue());
			ps.setBytes(2, m.getBytes());
			affected = ps.executeUpdate();
			if (affected < 0) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void setReorderingWindow(Connection txn, KeySetId k, TransportId t,
			long rotationPeriod, long base, byte[] bitmap) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE incomingKeys SET base = ?, bitmap = ?"
					+ " WHERE transportId = ? AND keySetId = ?"
					+ " AND rotationPeriod = ?";
			ps = txn.prepareStatement(sql);
			ps.setLong(1, base);
			ps.setBytes(2, bitmap);
			ps.setString(3, t.getString());
			ps.setInt(4, k.getInt());
			ps.setLong(5, rotationPeriod);
			int affected = ps.executeUpdate();
			if (affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void setTransportKeysActive(Connection txn, TransportId t,
			KeySetId k) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE outgoingKeys SET active = true"
					+ " WHERE transportId = ? AND keySetId = ?";
			ps = txn.prepareStatement(sql);
			ps.setString(1, t.getString());
			ps.setInt(2, k.getInt());
			int affected = ps.executeUpdate();
			if (affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void updateExpiryTime(Connection txn, ContactId c, MessageId m,
			int maxLatency) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT txCount FROM statuses"
					+ " WHERE messageId = ? AND contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setInt(2, c.getInt());
			rs = ps.executeQuery();
			if (!rs.next()) throw new DbStateException();
			int txCount = rs.getInt(1);
			if (rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			sql = "UPDATE statuses SET expiry = ?, txCount = txCount + 1"
					+ " WHERE messageId = ? AND contactId = ?";
			ps = txn.prepareStatement(sql);
			long now = clock.currentTimeMillis();
			ps.setLong(1, calculateExpiry(now, maxLatency, txCount));
			ps.setBytes(2, m.getBytes());
			ps.setInt(3, c.getInt());
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void updateTransportKeys(Connection txn, KeySet ks)
			throws DbException {
		PreparedStatement ps = null;
		try {
			// Update the outgoing keys
			String sql = "UPDATE outgoingKeys SET rotationPeriod = ?,"
					+ " tagKey = ?, headerKey = ?, stream = ?"
					+ " WHERE transportId = ? AND keySetId = ?";
			ps = txn.prepareStatement(sql);
			TransportKeys k = ks.getTransportKeys();
			OutgoingKeys outCurr = k.getCurrentOutgoingKeys();
			ps.setLong(1, outCurr.getRotationPeriod());
			ps.setBytes(2, outCurr.getTagKey().getBytes());
			ps.setBytes(3, outCurr.getHeaderKey().getBytes());
			ps.setLong(4, outCurr.getStreamCounter());
			ps.setString(5, k.getTransportId().getString());
			ps.setInt(6, ks.getKeySetId().getInt());
			int affected = ps.executeUpdate();
			if (affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
			// Update the incoming keys
			sql = "UPDATE incomingKeys SET rotationPeriod = ?,"
					+ " tagKey = ?, headerKey = ?, base = ?, bitmap = ?"
					+ " WHERE transportId = ? AND keySetId = ?"
					+ " AND periodOffset = ?";
			ps = txn.prepareStatement(sql);
			ps.setString(6, k.getTransportId().getString());
			ps.setInt(7, ks.getKeySetId().getInt());
			// Previous rotation period
			IncomingKeys inPrev = k.getPreviousIncomingKeys();
			ps.setLong(1, inPrev.getRotationPeriod());
			ps.setBytes(2, inPrev.getTagKey().getBytes());
			ps.setBytes(3, inPrev.getHeaderKey().getBytes());
			ps.setLong(4, inPrev.getWindowBase());
			ps.setBytes(5, inPrev.getWindowBitmap());
			ps.setInt(8, OFFSET_PREV);
			ps.addBatch();
			// Current rotation period
			IncomingKeys inCurr = k.getCurrentIncomingKeys();
			ps.setLong(1, inCurr.getRotationPeriod());
			ps.setBytes(2, inCurr.getTagKey().getBytes());
			ps.setBytes(3, inCurr.getHeaderKey().getBytes());
			ps.setLong(4, inCurr.getWindowBase());
			ps.setBytes(5, inCurr.getWindowBitmap());
			ps.setInt(8, OFFSET_CURR);
			ps.addBatch();
			// Next rotation period
			IncomingKeys inNext = k.getNextIncomingKeys();
			ps.setLong(1, inNext.getRotationPeriod());
			ps.setBytes(2, inNext.getTagKey().getBytes());
			ps.setBytes(3, inNext.getHeaderKey().getBytes());
			ps.setLong(4, inNext.getWindowBase());
			ps.setBytes(5, inNext.getWindowBitmap());
			ps.setInt(8, OFFSET_NEXT);
			ps.addBatch();
			int[] batchAffected = ps.executeBatch();
			if (batchAffected.length != 3) throw new DbStateException();
			for (int rows : batchAffected)
				if (rows < 0 || rows > 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}
}
