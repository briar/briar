package org.briarproject.bramble.db;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.PendingContact;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.crypto.AgreementPrivateKey;
import org.briarproject.bramble.api.crypto.AgreementPublicKey;
import org.briarproject.bramble.api.crypto.PrivateKey;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.crypto.SignaturePrivateKey;
import org.briarproject.bramble.api.crypto.SignaturePublicKey;
import org.briarproject.bramble.api.db.DataTooNewException;
import org.briarproject.bramble.api.db.DataTooOldException;
import org.briarproject.bramble.api.db.DbClosedException;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.MessageDeletedException;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.db.MigrationListener;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.Identity;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.Group.Visibility;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageFactory;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.MessageStatus;
import org.briarproject.bramble.api.sync.validation.MessageState;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.transport.IncomingKeys;
import org.briarproject.bramble.api.transport.KeySetId;
import org.briarproject.bramble.api.transport.OutgoingKeys;
import org.briarproject.bramble.api.transport.TransportKeySet;
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
import javax.annotation.concurrent.GuardedBy;

import static java.sql.Types.BINARY;
import static java.sql.Types.BOOLEAN;
import static java.sql.Types.INTEGER;
import static java.sql.Types.VARCHAR;
import static java.util.Arrays.asList;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.db.Metadata.REMOVE;
import static org.briarproject.bramble.api.sync.Group.Visibility.INVISIBLE;
import static org.briarproject.bramble.api.sync.Group.Visibility.SHARED;
import static org.briarproject.bramble.api.sync.Group.Visibility.VISIBLE;
import static org.briarproject.bramble.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;
import static org.briarproject.bramble.api.sync.validation.MessageState.DELIVERED;
import static org.briarproject.bramble.api.sync.validation.MessageState.PENDING;
import static org.briarproject.bramble.api.sync.validation.MessageState.UNKNOWN;
import static org.briarproject.bramble.db.DatabaseConstants.DB_SETTINGS_NAMESPACE;
import static org.briarproject.bramble.db.DatabaseConstants.DIRTY_KEY;
import static org.briarproject.bramble.db.DatabaseConstants.LAST_COMPACTED_KEY;
import static org.briarproject.bramble.db.DatabaseConstants.MAX_COMPACTION_INTERVAL_MS;
import static org.briarproject.bramble.db.DatabaseConstants.SCHEMA_VERSION_KEY;
import static org.briarproject.bramble.db.ExponentialBackoff.calculateExpiry;
import static org.briarproject.bramble.db.JdbcUtils.tryToClose;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.LogUtils.now;

/**
 * A generic database implementation that can be used with any JDBC-compatible
 * database library.
 */
@NotNullByDefault
abstract class JdbcDatabase implements Database<Connection> {

	// Package access for testing
	static final int CODE_SCHEMA_VERSION = 47;

	// Time period offsets for incoming transport keys
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
					+ " handshakePublicKey _BINARY," // Null if not generated
					+ " handshakePrivateKey _BINARY," // Null if not generated
					+ " created BIGINT NOT NULL,"
					+ " PRIMARY KEY (authorId))";

	private static final String CREATE_CONTACTS =
			"CREATE TABLE contacts"
					+ " (contactId _COUNTER,"
					+ " authorId _HASH NOT NULL,"
					+ " formatVersion INT NOT NULL,"
					+ " name _STRING NOT NULL,"
					+ " alias _STRING," // Null if no alias has been set
					+ " publicKey _BINARY NOT NULL,"
					+ " handshakePublicKey _BINARY," // Null if key is unknown
					+ " localAuthorId _HASH NOT NULL,"
					+ " verified BOOLEAN NOT NULL,"
					+ " syncVersions _BINARY DEFAULT '00' NOT NULL,"
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
					+ " temporary BOOLEAN NOT NULL,"
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
					+ " eta BIGINT NOT NULL,"
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

	private static final String CREATE_PENDING_CONTACTS =
			"CREATE TABLE pendingContacts"
					+ " (pendingContactId _HASH NOT NULL,"
					+ " publicKey _BINARY NOT NULL,"
					+ " alias _STRING NOT NULL,"
					+ " timestamp BIGINT NOT NULL,"
					+ " PRIMARY KEY (pendingContactId))";

	private static final String CREATE_OUTGOING_KEYS =
			"CREATE TABLE outgoingKeys"
					+ " (transportId _STRING NOT NULL,"
					+ " keySetId _COUNTER,"
					+ " timePeriod BIGINT NOT NULL,"
					+ " contactId INT," // Null if contact is pending
					+ " pendingContactId _HASH," // Null if not pending
					+ " tagKey _SECRET NOT NULL,"
					+ " headerKey _SECRET NOT NULL,"
					+ " stream BIGINT NOT NULL,"
					+ " active BOOLEAN NOT NULL,"
					+ " rootKey _SECRET," // Null for rotation keys
					+ " alice BOOLEAN," // Null for rotation keys
					+ " PRIMARY KEY (transportId, keySetId),"
					+ " FOREIGN KEY (transportId)"
					+ " REFERENCES transports (transportId)"
					+ " ON DELETE CASCADE,"
					+ " UNIQUE (keySetId),"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE,"
					+ " FOREIGN KEY (pendingContactId)"
					+ " REFERENCES pendingContacts (pendingContactId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_INCOMING_KEYS =
			"CREATE TABLE incomingKeys"
					+ " (transportId _STRING NOT NULL,"
					+ " keySetId INT NOT NULL,"
					+ " timePeriod BIGINT NOT NULL,"
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
			getLogger(JdbcDatabase.class.getName());

	private final MessageFactory messageFactory;
	private final Clock clock;
	private final DatabaseTypes dbTypes;

	private final Lock connectionsLock = new ReentrantLock();
	private final Condition connectionsChanged = connectionsLock.newCondition();

	@GuardedBy("connectionsLock")
	private final LinkedList<Connection> connections = new LinkedList<>();

	@GuardedBy("connectionsLock")
	private int openConnections = 0;
	@GuardedBy("connectionsLock")
	private boolean closed = false;

	private boolean wasDirtyOnInitialisation = false;

	protected abstract Connection createConnection()
			throws DbException, SQLException;

	// Used exclusively during open to compact the database after schema
	// migrations and after DatabaseConstants#MAX_COMPACTION_INTERVAL_MS has
	// elapsed
	protected abstract void compactAndClose() throws DbException;

	JdbcDatabase(DatabaseTypes databaseTypes, MessageFactory messageFactory,
			Clock clock) {
		this.dbTypes = databaseTypes;
		this.messageFactory = messageFactory;
		this.clock = clock;
	}

	protected void open(String driverClass, boolean reopen,
			@SuppressWarnings("unused") SecretKey key,
			@Nullable MigrationListener listener) throws DbException {
		// Load the JDBC driver
		try {
			Class.forName(driverClass);
		} catch (ClassNotFoundException e) {
			throw new DbException(e);
		}
		// Open the database and create the tables and indexes if necessary
		boolean compact;
		Connection txn = startTransaction();
		try {
			if (reopen) {
				Settings s = getSettings(txn, DB_SETTINGS_NAMESPACE);
				wasDirtyOnInitialisation = isDirty(s);
				compact = migrateSchema(txn, s, listener) || isCompactionDue(s);
			} else {
				wasDirtyOnInitialisation = false;
				createTables(txn);
				initialiseSettings(txn);
				compact = false;
			}
			if (LOG.isLoggable(INFO)) {
				LOG.info("db dirty? " + wasDirtyOnInitialisation);
			}
			createIndexes(txn);
			setDirty(txn, true);
			commitTransaction(txn);
		} catch (DbException e) {
			abortTransaction(txn);
			throw e;
		}
		// Compact the database if necessary
		if (compact) {
			if (listener != null) listener.onDatabaseCompaction();
			long start = now();
			compactAndClose();
			logDuration(LOG, "Compacting database", start);
			// Allow the next transaction to reopen the DB
			synchronized (connectionsLock) {
				closed = false;
			}
			txn = startTransaction();
			try {
				storeLastCompacted(txn);
				commitTransaction(txn);
			} catch (DbException e) {
				abortTransaction(txn);
				throw e;
			}
		}
	}

	@Override
	public boolean wasDirtyOnInitialisation() {
		return wasDirtyOnInitialisation;
	}

	/**
	 * Compares the schema version stored in the database with the schema
	 * version used by the current code and applies any suitable migrations to
	 * the data if necessary.
	 *
	 * @return true if any migrations were applied, false if the schema was
	 * already current
	 * @throws DataTooNewException if the data uses a newer schema than the
	 * current code
	 * @throws DataTooOldException if the data uses an older schema than the
	 * current code and cannot be migrated
	 */
	private boolean migrateSchema(Connection txn, Settings s,
			@Nullable MigrationListener listener) throws DbException {
		int dataSchemaVersion = s.getInt(SCHEMA_VERSION_KEY, -1);
		if (dataSchemaVersion == -1) throw new DbException();
		if (dataSchemaVersion == CODE_SCHEMA_VERSION) return false;
		if (CODE_SCHEMA_VERSION < dataSchemaVersion)
			throw new DataTooNewException();
		// Apply any suitable migrations in order
		for (Migration<Connection> m : getMigrations()) {
			int start = m.getStartVersion(), end = m.getEndVersion();
			if (start == dataSchemaVersion) {
				if (LOG.isLoggable(INFO))
					LOG.info("Migrating from schema " + start + " to " + end);
				if (listener != null) listener.onDatabaseMigration();
				// Apply the migration
				m.migrate(txn);
				// Store the new schema version
				storeSchemaVersion(txn, end);
				dataSchemaVersion = end;
			}
		}
		if (dataSchemaVersion != CODE_SCHEMA_VERSION)
			throw new DataTooOldException();
		return true;
	}

	// Package access for testing
	List<Migration<Connection>> getMigrations() {
		return asList(
				new Migration38_39(),
				new Migration39_40(),
				new Migration40_41(dbTypes),
				new Migration41_42(dbTypes),
				new Migration42_43(dbTypes),
				new Migration43_44(dbTypes),
				new Migration44_45(),
				new Migration45_46(),
				new Migration46_47(dbTypes)
		);
	}

	private boolean isCompactionDue(Settings s) {
		long lastCompacted = s.getLong(LAST_COMPACTED_KEY, 0);
		long elapsed = clock.currentTimeMillis() - lastCompacted;
		if (LOG.isLoggable(INFO))
			LOG.info(elapsed + " ms since last compaction");
		return elapsed > MAX_COMPACTION_INTERVAL_MS;
	}

	private void storeSchemaVersion(Connection txn, int version)
			throws DbException {
		Settings s = new Settings();
		s.putInt(SCHEMA_VERSION_KEY, version);
		mergeSettings(txn, s, DB_SETTINGS_NAMESPACE);
	}

	private void storeLastCompacted(Connection txn) throws DbException {
		Settings s = new Settings();
		s.putLong(LAST_COMPACTED_KEY, clock.currentTimeMillis());
		mergeSettings(txn, s, DB_SETTINGS_NAMESPACE);
	}

	private boolean isDirty(Settings s) {
		return s.getBoolean(DIRTY_KEY, false);
	}

	protected void setDirty(Connection txn, boolean dirty) throws DbException {
		Settings s = new Settings();
		s.putBoolean(DIRTY_KEY, dirty);
		mergeSettings(txn, s, DB_SETTINGS_NAMESPACE);
	}

	private void initialiseSettings(Connection txn) throws DbException {
		Settings s = new Settings();
		s.putInt(SCHEMA_VERSION_KEY, CODE_SCHEMA_VERSION);
		s.putLong(LAST_COMPACTED_KEY, clock.currentTimeMillis());
		mergeSettings(txn, s, DB_SETTINGS_NAMESPACE);
	}

	private void createTables(Connection txn) throws DbException {
		Statement s = null;
		try {
			s = txn.createStatement();
			s.executeUpdate(dbTypes.replaceTypes(CREATE_SETTINGS));
			s.executeUpdate(dbTypes.replaceTypes(CREATE_LOCAL_AUTHORS));
			s.executeUpdate(dbTypes.replaceTypes(CREATE_CONTACTS));
			s.executeUpdate(dbTypes.replaceTypes(CREATE_GROUPS));
			s.executeUpdate(dbTypes.replaceTypes(CREATE_GROUP_METADATA));
			s.executeUpdate(dbTypes.replaceTypes(CREATE_GROUP_VISIBILITIES));
			s.executeUpdate(dbTypes.replaceTypes(CREATE_MESSAGES));
			s.executeUpdate(dbTypes.replaceTypes(CREATE_MESSAGE_METADATA));
			s.executeUpdate(dbTypes.replaceTypes(CREATE_MESSAGE_DEPENDENCIES));
			s.executeUpdate(dbTypes.replaceTypes(CREATE_OFFERS));
			s.executeUpdate(dbTypes.replaceTypes(CREATE_STATUSES));
			s.executeUpdate(dbTypes.replaceTypes(CREATE_TRANSPORTS));
			s.executeUpdate(dbTypes.replaceTypes(CREATE_PENDING_CONTACTS));
			s.executeUpdate(dbTypes.replaceTypes(CREATE_OUTGOING_KEYS));
			s.executeUpdate(dbTypes.replaceTypes(CREATE_INCOMING_KEYS));
			s.close();
		} catch (SQLException e) {
			tryToClose(s, LOG, WARNING);
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
			tryToClose(s, LOG, WARNING);
			throw new DbException(e);
		}
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
			tryToClose(txn, LOG, WARNING);
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
			@Nullable PublicKey handshake, boolean verified)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Create a contact row
			String sql = "INSERT INTO contacts"
					+ " (authorId, formatVersion, name, publicKey,"
					+ " localAuthorId, handshakePublicKey, verified)"
					+ " VALUES (?, ?, ?, ?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, remote.getId().getBytes());
			ps.setInt(2, remote.getFormatVersion());
			ps.setString(3, remote.getName());
			ps.setBytes(4, remote.getPublicKey().getEncoded());
			ps.setBytes(5, local.getBytes());
			if (handshake == null) ps.setNull(6, BINARY);
			else ps.setBytes(6, handshake.getEncoded());
			ps.setBoolean(7, verified);
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(ps, LOG, WARNING);
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
				MessageState state = MessageState.fromValue(rs.getInt(3));
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public void addIdentity(Connection txn, Identity i) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO localAuthors"
					+ " (authorId, formatVersion, name, publicKey, privateKey,"
					+ " handshakePublicKey, handshakePrivateKey, created)"
					+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			LocalAuthor local = i.getLocalAuthor();
			ps.setBytes(1, local.getId().getBytes());
			ps.setInt(2, local.getFormatVersion());
			ps.setString(3, local.getName());
			ps.setBytes(4, local.getPublicKey().getEncoded());
			ps.setBytes(5, local.getPrivateKey().getEncoded());
			if (i.getHandshakePublicKey() == null) ps.setNull(6, BINARY);
			else ps.setBytes(6, i.getHandshakePublicKey().getEncoded());
			if (i.getHandshakePrivateKey() == null) ps.setNull(7, BINARY);
			else ps.setBytes(7, i.getHandshakePrivateKey().getEncoded());
			ps.setLong(8, i.getTimeCreated());
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public void addMessage(Connection txn, Message m, MessageState state,
			boolean shared, boolean temporary, @Nullable ContactId sender)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO messages (messageId, groupId, timestamp,"
					+ " state, shared, temporary, length, raw)"
					+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getId().getBytes());
			ps.setBytes(2, m.getGroupId().getBytes());
			ps.setLong(3, m.getTimestamp());
			ps.setInt(4, state.getValue());
			ps.setBoolean(5, shared);
			ps.setBoolean(6, temporary);
			byte[] raw = messageFactory.getRawMessage(m);
			ps.setInt(7, raw.length);
			ps.setBytes(8, raw);
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
			// Create a status row for each contact that can see the group
			Map<ContactId, Boolean> visibility =
					getGroupVisibility(txn, m.getGroupId());
			for (Entry<ContactId, Boolean> e : visibility.entrySet()) {
				ContactId c = e.getKey();
				boolean offered = removeOfferedMessage(txn, c, m.getId());
				boolean seen = offered || c.equals(sender);
				addStatus(txn, m.getId(), c, m.getGroupId(), m.getTimestamp(),
						raw.length, state, e.getValue(), shared, false, seen);
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
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	private void addStatus(Connection txn, MessageId m, ContactId c, GroupId g,
			long timestamp, int length, MessageState state, boolean groupShared,
			boolean messageShared, boolean deleted, boolean seen)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO statuses (messageId, contactId, groupId,"
					+ " timestamp, length, state, groupShared, messageShared,"
					+ " deleted, ack, seen, requested, expiry, txCount, eta)"
					+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE, 0, 0,"
					+ " 0)";
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
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public void addMessageDependency(Connection txn, Message dependent,
			MessageId dependency, MessageState dependentState)
			throws DbException {
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
			MessageState dependencyState = null;
			if (rs.next()) {
				dependencyState = MessageState.fromValue(rs.getInt(1));
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public void addPendingContact(Connection txn, PendingContact p)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO pendingContacts (pendingContactId,"
					+ " publicKey, alias, timestamp)"
					+ " VALUES (?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, p.getId().getBytes());
			ps.setBytes(2, p.getPublicKey().getEncoded());
			ps.setString(3, p.getAlias());
			ps.setLong(4, p.getTimestamp());
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public KeySetId addTransportKeys(Connection txn, ContactId c,
			TransportKeys k) throws DbException {
		return addTransportKeys(txn, c, null, k);
	}

	@Override
	public KeySetId addTransportKeys(Connection txn,
			PendingContactId p, TransportKeys k) throws DbException {
		return addTransportKeys(txn, null, p, k);
	}

	private KeySetId addTransportKeys(Connection txn,
			@Nullable ContactId c, @Nullable PendingContactId p,
			TransportKeys k) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Store the outgoing keys
			String sql = "INSERT INTO outgoingKeys (transportId, timePeriod,"
					+ " contactId, pendingContactId, tagKey, headerKey,"
					+ " stream, active, rootKey, alice)"
					+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setString(1, k.getTransportId().getString());
			ps.setLong(2, k.getTimePeriod());
			if (c == null) ps.setNull(3, INTEGER);
			else ps.setInt(3, c.getInt());
			if (p == null) ps.setNull(4, BINARY);
			else ps.setBytes(4, p.getBytes());
			OutgoingKeys outCurr = k.getCurrentOutgoingKeys();
			ps.setBytes(5, outCurr.getTagKey().getBytes());
			ps.setBytes(6, outCurr.getHeaderKey().getBytes());
			ps.setLong(7, outCurr.getStreamCounter());
			ps.setBoolean(8, outCurr.isActive());
			if (k.isHandshakeMode()) {
				ps.setBytes(9, k.getRootKey().getBytes());
				ps.setBoolean(10, k.isAlice());
			} else {
				ps.setNull(9, BINARY);
				ps.setNull(10, BOOLEAN);
			}
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
			sql = "INSERT INTO incomingKeys (transportId, keySetId,"
					+ " timePeriod, tagKey, headerKey, base, bitmap,"
					+ " periodOffset)"
					+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setString(1, k.getTransportId().getString());
			ps.setInt(2, keySetId.getInt());
			// Previous time period
			IncomingKeys inPrev = k.getPreviousIncomingKeys();
			ps.setLong(3, inPrev.getTimePeriod());
			ps.setBytes(4, inPrev.getTagKey().getBytes());
			ps.setBytes(5, inPrev.getHeaderKey().getBytes());
			ps.setLong(6, inPrev.getWindowBase());
			ps.setBytes(7, inPrev.getWindowBitmap());
			ps.setInt(8, OFFSET_PREV);
			ps.addBatch();
			// Current time period
			IncomingKeys inCurr = k.getCurrentIncomingKeys();
			ps.setLong(3, inCurr.getTimePeriod());
			ps.setBytes(4, inCurr.getTagKey().getBytes());
			ps.setBytes(5, inCurr.getHeaderKey().getBytes());
			ps.setLong(6, inCurr.getWindowBase());
			ps.setBytes(7, inCurr.getWindowBitmap());
			ps.setInt(8, OFFSET_CURR);
			ps.addBatch();
			// Next time period
			IncomingKeys inNext = k.getNextIncomingKeys();
			ps.setLong(3, inNext.getTimePeriod());
			ps.setBytes(4, inNext.getTagKey().getBytes());
			ps.setBytes(5, inNext.getHeaderKey().getBytes());
			ps.setLong(6, inNext.getWindowBase());
			ps.setBytes(7, inNext.getWindowBitmap());
			ps.setInt(8, OFFSET_NEXT);
			ps.addBatch();
			int[] batchAffected = ps.executeBatch();
			if (batchAffected.length != 3) throw new DbStateException();
			for (int rows : batchAffected)
				if (rows != 1) throw new DbStateException();
			ps.close();
			return keySetId;
		} catch (SQLException e) {
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public boolean containsIdentity(Connection txn, AuthorId a)
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public boolean containsPendingContact(Connection txn, PendingContactId p)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT NULL FROM pendingContacts"
					+ " WHERE pendingContactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, p.getBytes());
			rs = ps.executeQuery();
			boolean found = rs.next();
			if (rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return found;
		} catch (SQLException e) {
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public Contact getContact(Connection txn, ContactId c) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT authorId, formatVersion, name, alias,"
					+ " publicKey, handshakePublicKey, localAuthorId, verified"
					+ " FROM contacts"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			if (!rs.next()) throw new DbStateException();
			AuthorId authorId = new AuthorId(rs.getBytes(1));
			int formatVersion = rs.getInt(2);
			String name = rs.getString(3);
			String alias = rs.getString(4);
			PublicKey publicKey = new SignaturePublicKey(rs.getBytes(5));
			byte[] handshakePub = rs.getBytes(6);
			AuthorId localAuthorId = new AuthorId(rs.getBytes(7));
			boolean verified = rs.getBoolean(8);
			rs.close();
			ps.close();
			Author author =
					new Author(authorId, formatVersion, name, publicKey);
			PublicKey handshakePublicKey = handshakePub == null ?
					null : new AgreementPublicKey(handshakePub);
			return new Contact(c, author, localAuthorId, alias,
					handshakePublicKey, verified);
		} catch (SQLException e) {
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public Collection<Contact> getContacts(Connection txn) throws DbException {
		Statement s = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT contactId, authorId, formatVersion, name,"
					+ " alias, publicKey, handshakePublicKey, localAuthorId,"
					+ " verified"
					+ " FROM contacts";
			s = txn.createStatement();
			rs = s.executeQuery(sql);
			List<Contact> contacts = new ArrayList<>();
			while (rs.next()) {
				ContactId contactId = new ContactId(rs.getInt(1));
				AuthorId authorId = new AuthorId(rs.getBytes(2));
				int formatVersion = rs.getInt(3);
				String name = rs.getString(4);
				String alias = rs.getString(5);
				PublicKey publicKey = new SignaturePublicKey(rs.getBytes(6));
				byte[] handshakePub = rs.getBytes(7);
				AuthorId localAuthorId = new AuthorId(rs.getBytes(8));
				boolean verified = rs.getBoolean(9);
				Author author =
						new Author(authorId, formatVersion, name, publicKey);
				PublicKey handshakePublicKey = handshakePub == null ?
						null : new AgreementPublicKey(handshakePub);
				contacts.add(new Contact(contactId, author, localAuthorId,
						alias, handshakePublicKey, verified));
			}
			rs.close();
			s.close();
			return contacts;
		} catch (SQLException e) {
			tryToClose(rs, LOG, WARNING);
			tryToClose(s, LOG, WARNING);
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public Collection<Contact> getContactsByAuthorId(Connection txn,
			AuthorId remote) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT contactId, formatVersion, name, alias,"
					+ " publicKey, handshakePublicKey, localAuthorId, verified"
					+ " FROM contacts"
					+ " WHERE authorId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, remote.getBytes());
			rs = ps.executeQuery();
			List<Contact> contacts = new ArrayList<>();
			while (rs.next()) {
				ContactId contactId = new ContactId(rs.getInt(1));
				int formatVersion = rs.getInt(2);
				String name = rs.getString(3);
				String alias = rs.getString(4);
				PublicKey publicKey = new SignaturePublicKey(rs.getBytes(5));
				byte[] handshakePub = rs.getBytes(6);
				AuthorId localAuthorId = new AuthorId(rs.getBytes(7));
				boolean verified = rs.getBoolean(8);
				Author author =
						new Author(remote, formatVersion, name, publicKey);
				PublicKey handshakePublicKey = handshakePub == null ?
						null : new AgreementPublicKey(handshakePub);
				contacts.add(new Contact(contactId, author, localAuthorId,
						alias, handshakePublicKey, verified));
			}
			rs.close();
			ps.close();
			return contacts;
		} catch (SQLException e) {
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Nullable
	@Override
	public Contact getContact(Connection txn, PublicKey handshakePublicKey,
			AuthorId localAuthorId) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT contactId, authorId, formatVersion, name,"
					+ " alias, publicKey, verified"
					+ " FROM contacts"
					+ " WHERE handshakePublicKey = ? AND localAuthorId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, handshakePublicKey.getEncoded());
			ps.setBytes(2, localAuthorId.getBytes());
			rs = ps.executeQuery();
			if (!rs.next()) {
				rs.close();
				ps.close();
				return null;
			}
			ContactId contactId = new ContactId(rs.getInt(1));
			AuthorId authorId = new AuthorId(rs.getBytes(2));
			int formatVersion = rs.getInt(3);
			String name = rs.getString(4);
			String alias = rs.getString(5);
			PublicKey publicKey = new SignaturePublicKey(rs.getBytes(6));
			boolean verified = rs.getBoolean(7);
			if (rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			Author author =
					new Author(authorId, formatVersion, name, publicKey);
			return new Contact(contactId, author, localAuthorId, alias,
					handshakePublicKey, verified);
		} catch (SQLException e) {
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public Identity getIdentity(Connection txn, AuthorId a) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT formatVersion, name, publicKey, privateKey,"
					+ " handshakePublicKey, handshakePrivateKey, created"
					+ " FROM localAuthors"
					+ " WHERE authorId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, a.getBytes());
			rs = ps.executeQuery();
			if (!rs.next()) throw new DbStateException();
			int formatVersion = rs.getInt(1);
			String name = rs.getString(2);
			PublicKey publicKey = new SignaturePublicKey(rs.getBytes(3));
			PrivateKey privateKey = new SignaturePrivateKey(rs.getBytes(4));
			byte[] handshakePub = rs.getBytes(5);
			byte[] handshakePriv = rs.getBytes(6);
			long created = rs.getLong(7);
			if (rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			LocalAuthor local = new LocalAuthor(a, formatVersion, name,
					publicKey, privateKey);
			PublicKey handshakePublicKey = handshakePub == null ?
					null : new AgreementPublicKey(handshakePub);
			PrivateKey handshakePrivateKey = handshakePriv == null ?
					null : new AgreementPrivateKey(handshakePriv);
			return new Identity(local, handshakePublicKey, handshakePrivateKey,
					created);
		} catch (SQLException e) {
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public Collection<Identity> getIdentities(Connection txn)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT authorId, formatVersion, name, publicKey,"
					+ " privateKey, handshakePublicKey, handshakePrivateKey,"
					+ " created"
					+ " FROM localAuthors";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			List<Identity> identities = new ArrayList<>();
			while (rs.next()) {
				AuthorId authorId = new AuthorId(rs.getBytes(1));
				int formatVersion = rs.getInt(2);
				String name = rs.getString(3);
				PublicKey publicKey = new SignaturePublicKey(rs.getBytes(4));
				PrivateKey privateKey = new SignaturePrivateKey(rs.getBytes(5));
				byte[] handshakePub = rs.getBytes(6);
				byte[] handshakePriv = rs.getBytes(7);
				long created = rs.getLong(8);
				LocalAuthor local = new LocalAuthor(authorId, formatVersion,
						name, publicKey, privateKey);
				PublicKey handshakePublicKey = handshakePub == null ?
						null : new AgreementPublicKey(handshakePub);
				PrivateKey handshakePrivateKey = handshakePriv == null ?
						null : new AgreementPrivateKey(handshakePriv);
				identities.add(new Identity(local, handshakePublicKey,
						handshakePrivateKey, created));
			}
			rs.close();
			ps.close();
			return identities;
		} catch (SQLException e) {
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public Message getMessage(Connection txn, MessageId m) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT groupId, timestamp, raw FROM messages"
					+ " WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			rs = ps.executeQuery();
			if (!rs.next()) throw new DbStateException();
			GroupId g = new GroupId(rs.getBytes(1));
			long timestamp = rs.getLong(2);
			byte[] raw = rs.getBytes(3);
			if (rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			if (raw == null) throw new MessageDeletedException();
			if (raw.length <= MESSAGE_HEADER_LENGTH) throw new AssertionError();
			byte[] body = new byte[raw.length - MESSAGE_HEADER_LENGTH];
			System.arraycopy(raw, MESSAGE_HEADER_LENGTH, body, 0, body.length);
			return new Message(m, g, timestamp, body);
		} catch (SQLException e) {
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public Map<MessageId, MessageState> getMessageDependencies(Connection txn,
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
			Map<MessageId, MessageState> dependencies = new HashMap<>();
			while (rs.next()) {
				MessageId dependency = new MessageId(rs.getBytes(1));
				MessageState state = MessageState.fromValue(rs.getInt(2));
				if (rs.wasNull())
					state = UNKNOWN; // Missing or in a different group
				dependencies.put(dependency, state);
			}
			rs.close();
			ps.close();
			return dependencies;
		} catch (SQLException e) {
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public Map<MessageId, MessageState> getMessageDependents(Connection txn,
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
			Map<MessageId, MessageState> dependents = new HashMap<>();
			while (rs.next()) {
				MessageId dependent = new MessageId(rs.getBytes(1));
				MessageState state = MessageState.fromValue(rs.getInt(2));
				dependents.put(dependent, state);
			}
			rs.close();
			ps.close();
			return dependents;
		} catch (SQLException e) {
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public MessageState getMessageState(Connection txn, MessageId m)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT state FROM messages WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			rs = ps.executeQuery();
			if (!rs.next()) throw new DbStateException();
			MessageState state = MessageState.fromValue(rs.getInt(1));
			if (rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return state;
		} catch (SQLException e) {
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public Collection<MessageId> getMessagesToOffer(Connection txn,
			ContactId c, int maxMessages, int maxLatency) throws DbException {
		long now = clock.currentTimeMillis();
		long eta = now + maxLatency;
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT messageId FROM statuses"
					+ " WHERE contactId = ? AND state = ?"
					+ " AND groupShared = TRUE AND messageShared = TRUE"
					+ " AND deleted = FALSE"
					+ " AND seen = FALSE AND requested = FALSE"
					+ " AND (expiry <= ? OR eta > ?)"
					+ " ORDER BY timestamp LIMIT ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setInt(2, DELIVERED.getValue());
			ps.setLong(3, now);
			ps.setLong(4, eta);
			ps.setInt(5, maxMessages);
			rs = ps.executeQuery();
			List<MessageId> ids = new ArrayList<>();
			while (rs.next()) ids.add(new MessageId(rs.getBytes(1)));
			rs.close();
			ps.close();
			return ids;
		} catch (SQLException e) {
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public Collection<MessageId> getMessagesToSend(Connection txn, ContactId c,
			int maxLength, int maxLatency) throws DbException {
		long now = clock.currentTimeMillis();
		long eta = now + maxLatency;
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT length, messageId FROM statuses"
					+ " WHERE contactId = ? AND state = ?"
					+ " AND groupShared = TRUE AND messageShared = TRUE"
					+ " AND deleted = FALSE"
					+ " AND seen = FALSE"
					+ " AND (expiry <= ? OR eta > ?)"
					+ " ORDER BY timestamp";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setInt(2, DELIVERED.getValue());
			ps.setLong(3, now);
			ps.setLong(4, eta);
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
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
			MessageState state) throws DbException {
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public PendingContact getPendingContact(Connection txn, PendingContactId p)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT publicKey, alias, timestamp"
					+ " FROM pendingContacts"
					+ " WHERE pendingContactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, p.getBytes());
			rs = ps.executeQuery();
			if (!rs.next()) throw new DbStateException();
			PublicKey publicKey = new AgreementPublicKey(rs.getBytes(1));
			String alias = rs.getString(2);
			long timestamp = rs.getLong(3);
			return new PendingContact(p, publicKey, alias, timestamp);
		} catch (SQLException e) {
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public Collection<PendingContact> getPendingContacts(Connection txn)
			throws DbException {
		Statement s = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT pendingContactId, publicKey, alias, timestamp"
					+ " FROM pendingContacts";
			s = txn.createStatement();
			rs = s.executeQuery(sql);
			List<PendingContact> pendingContacts = new ArrayList<>();
			while (rs.next()) {
				PendingContactId id = new PendingContactId(rs.getBytes(1));
				PublicKey publicKey = new AgreementPublicKey(rs.getBytes(2));
				String alias = rs.getString(3);
				long timestamp = rs.getLong(4);
				pendingContacts.add(new PendingContact(id, publicKey, alias,
						timestamp));
			}
			rs.close();
			s.close();
			return pendingContacts;
		} catch (SQLException e) {
			tryToClose(rs, LOG, WARNING);
			tryToClose(s, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public Collection<MessageId> getRequestedMessagesToSend(Connection txn,
			ContactId c, int maxLength, int maxLatency) throws DbException {
		long now = clock.currentTimeMillis();
		long eta = now + maxLatency;
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT length, messageId FROM statuses"
					+ " WHERE contactId = ? AND state = ?"
					+ " AND groupShared = TRUE AND messageShared = TRUE"
					+ " AND deleted = FALSE"
					+ " AND seen = FALSE AND requested = TRUE"
					+ " AND (expiry <= ? OR eta > ?)"
					+ " ORDER BY timestamp";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setInt(2, DELIVERED.getValue());
			ps.setLong(3, now);
			ps.setLong(4, eta);
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public List<Byte> getSyncVersions(Connection txn, ContactId c)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT syncVersions FROM contacts"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			if (!rs.next()) throw new DbStateException();
			byte[] bytes = rs.getBytes(1);
			List<Byte> supported = new ArrayList<>(bytes.length);
			for (byte b : bytes) supported.add(b);
			if (rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return supported;
		} catch (SQLException e) {
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public Collection<TransportKeySet> getTransportKeys(Connection txn,
			TransportId t) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Retrieve the incoming keys
			String sql = "SELECT timePeriod, tagKey, headerKey, base, bitmap"
					+ " FROM incomingKeys"
					+ " WHERE transportId = ?"
					+ " ORDER BY keySetId, periodOffset";
			ps = txn.prepareStatement(sql);
			ps.setString(1, t.getString());
			rs = ps.executeQuery();
			List<IncomingKeys> inKeys = new ArrayList<>();
			while (rs.next()) {
				long timePeriod = rs.getLong(1);
				SecretKey tagKey = new SecretKey(rs.getBytes(2));
				SecretKey headerKey = new SecretKey(rs.getBytes(3));
				long windowBase = rs.getLong(4);
				byte[] windowBitmap = rs.getBytes(5);
				inKeys.add(new IncomingKeys(tagKey, headerKey, timePeriod,
						windowBase, windowBitmap));
			}
			rs.close();
			ps.close();
			// Retrieve the outgoing keys in the same order
			sql = "SELECT keySetId, timePeriod, contactId, pendingContactId,"
					+ " tagKey, headerKey, stream, active, rootKey, alice"
					+ " FROM outgoingKeys"
					+ " WHERE transportId = ?"
					+ " ORDER BY keySetId";
			ps = txn.prepareStatement(sql);
			ps.setString(1, t.getString());
			rs = ps.executeQuery();
			Collection<TransportKeySet> keys = new ArrayList<>();
			for (int i = 0; rs.next(); i++) {
				// There should be three times as many incoming keys
				if (inKeys.size() < (i + 1) * 3) throw new DbStateException();
				KeySetId keySetId = new KeySetId(rs.getInt(1));
				long timePeriod = rs.getLong(2);
				int cId = rs.getInt(3);
				ContactId contactId = rs.wasNull() ? null : new ContactId(cId);
				byte[] pId = rs.getBytes(4);
				PendingContactId pendingContactId = pId == null ?
						null : new PendingContactId(pId);
				SecretKey tagKey = new SecretKey(rs.getBytes(5));
				SecretKey headerKey = new SecretKey(rs.getBytes(6));
				long streamCounter = rs.getLong(7);
				boolean active = rs.getBoolean(8);
				byte[] rootKey = rs.getBytes(9);
				boolean alice = rs.getBoolean(10);
				OutgoingKeys outCurr = new OutgoingKeys(tagKey, headerKey,
						timePeriod, streamCounter, active);
				IncomingKeys inPrev = inKeys.get(i * 3);
				IncomingKeys inCurr = inKeys.get(i * 3 + 1);
				IncomingKeys inNext = inKeys.get(i * 3 + 2);
				TransportKeys transportKeys;
				if (rootKey == null) {
					transportKeys = new TransportKeys(t, inPrev, inCurr,
							inNext, outCurr);
				} else {
					transportKeys = new TransportKeys(t, inPrev, inCurr,
							inNext, outCurr, new SecretKey(rootKey), alice);
				}
				keys.add(new TransportKeySet(keySetId, contactId,
						pendingContactId, transportKeys));
			}
			rs.close();
			ps.close();
			return keys;
		} catch (SQLException e) {
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(ps, LOG, WARNING);
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
			MessageState state = MessageState.fromValue(rs.getInt(2));
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
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public void removeIdentity(Connection txn, AuthorId a) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "DELETE FROM localAuthors WHERE authorId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, a.getBytes());
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public void removePendingContact(Connection txn, PendingContactId p)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "DELETE FROM pendingContacts"
					+ " WHERE pendingContactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, p.getBytes());
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public void removeTemporaryMessages(Connection txn) throws DbException {
		Statement s = null;
		try {
			String sql = "DELETE FROM messages WHERE temporary = TRUE";
			s = txn.createStatement();
			int affected = s.executeUpdate(sql);
			if (affected < 0) throw new DbStateException();
			s.close();
		} catch (SQLException e) {
			tryToClose(s, LOG, WARNING);
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
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public void setContactAlias(Connection txn, ContactId c,
			@Nullable String alias) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE contacts SET alias = ? WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			if (alias == null) ps.setNull(1, VARCHAR);
			else ps.setString(1, alias);
			ps.setInt(2, c.getInt());
			int affected = ps.executeUpdate();
			if (affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public void setHandshakeKeyPair(Connection txn, AuthorId local,
			PublicKey publicKey, PrivateKey privateKey) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE localAuthors"
					+ " SET handshakePublicKey = ?, handshakePrivateKey = ?"
					+ " WHERE authorId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, publicKey.getEncoded());
			ps.setBytes(2, privateKey.getEncoded());
			ps.setBytes(3, local.getBytes());
			int affected = ps.executeUpdate();
			if (affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public void setMessagePermanent(Connection txn, MessageId m)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE messages SET temporary = FALSE"
					+ " WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			int affected = ps.executeUpdate();
			if (affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public void setMessageState(Connection txn, MessageId m, MessageState state)
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
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public void setReorderingWindow(Connection txn, KeySetId k,
			TransportId t, long timePeriod, long base, byte[] bitmap)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE incomingKeys SET base = ?, bitmap = ?"
					+ " WHERE transportId = ? AND keySetId = ?"
					+ " AND timePeriod = ?";
			ps = txn.prepareStatement(sql);
			ps.setLong(1, base);
			ps.setBytes(2, bitmap);
			ps.setString(3, t.getString());
			ps.setInt(4, k.getInt());
			ps.setLong(5, timePeriod);
			int affected = ps.executeUpdate();
			if (affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public void setSyncVersions(Connection txn, ContactId c,
			List<Byte> supported) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE contacts SET syncVersions = ?"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			byte[] bytes = new byte[supported.size()];
			for (int i = 0; i < bytes.length; i++) {
				bytes[i] = supported.get(i);
			}
			ps.setBytes(1, bytes);
			ps.setInt(2, c.getInt());
			int affected = ps.executeUpdate();
			if (affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps, LOG, WARNING);
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
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public void updateExpiryTimeAndEta(Connection txn, ContactId c, MessageId m,
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
			sql = "UPDATE statuses"
					+ " SET expiry = ?, txCount = txCount + 1, eta = ?"
					+ " WHERE messageId = ? AND contactId = ?";
			ps = txn.prepareStatement(sql);
			long now = clock.currentTimeMillis();
			long eta = now + maxLatency;
			ps.setLong(1, calculateExpiry(now, maxLatency, txCount));
			ps.setLong(2, eta);
			ps.setBytes(3, m.getBytes());
			ps.setInt(4, c.getInt());
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public void updateTransportKeys(Connection txn, TransportKeySet ks)
			throws DbException {
		PreparedStatement ps = null;
		try {
			// Update the outgoing keys
			String sql = "UPDATE outgoingKeys SET timePeriod = ?,"
					+ " tagKey = ?, headerKey = ?, stream = ?"
					+ " WHERE transportId = ? AND keySetId = ?";
			ps = txn.prepareStatement(sql);
			TransportKeys k = ks.getKeys();
			OutgoingKeys outCurr = k.getCurrentOutgoingKeys();
			ps.setLong(1, outCurr.getTimePeriod());
			ps.setBytes(2, outCurr.getTagKey().getBytes());
			ps.setBytes(3, outCurr.getHeaderKey().getBytes());
			ps.setLong(4, outCurr.getStreamCounter());
			ps.setString(5, k.getTransportId().getString());
			ps.setInt(6, ks.getKeySetId().getInt());
			int affected = ps.executeUpdate();
			if (affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
			// Update the incoming keys
			sql = "UPDATE incomingKeys SET timePeriod = ?,"
					+ " tagKey = ?, headerKey = ?, base = ?, bitmap = ?"
					+ " WHERE transportId = ? AND keySetId = ?"
					+ " AND periodOffset = ?";
			ps = txn.prepareStatement(sql);
			ps.setString(6, k.getTransportId().getString());
			ps.setInt(7, ks.getKeySetId().getInt());
			// Previous time period
			IncomingKeys inPrev = k.getPreviousIncomingKeys();
			ps.setLong(1, inPrev.getTimePeriod());
			ps.setBytes(2, inPrev.getTagKey().getBytes());
			ps.setBytes(3, inPrev.getHeaderKey().getBytes());
			ps.setLong(4, inPrev.getWindowBase());
			ps.setBytes(5, inPrev.getWindowBitmap());
			ps.setInt(8, OFFSET_PREV);
			ps.addBatch();
			// Current time period
			IncomingKeys inCurr = k.getCurrentIncomingKeys();
			ps.setLong(1, inCurr.getTimePeriod());
			ps.setBytes(2, inCurr.getTagKey().getBytes());
			ps.setBytes(3, inCurr.getHeaderKey().getBytes());
			ps.setLong(4, inCurr.getWindowBase());
			ps.setBytes(5, inCurr.getWindowBitmap());
			ps.setInt(8, OFFSET_CURR);
			ps.addBatch();
			// Next time period
			IncomingKeys inNext = k.getNextIncomingKeys();
			ps.setLong(1, inNext.getTimePeriod());
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
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}
}
