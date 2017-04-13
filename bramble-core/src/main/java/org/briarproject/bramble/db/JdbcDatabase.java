package org.briarproject.bramble.db;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DbClosedException;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Metadata;
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

import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.api.db.Metadata.REMOVE;
import static org.briarproject.bramble.api.sync.Group.Visibility.INVISIBLE;
import static org.briarproject.bramble.api.sync.Group.Visibility.SHARED;
import static org.briarproject.bramble.api.sync.Group.Visibility.VISIBLE;
import static org.briarproject.bramble.api.sync.ValidationManager.State.DELIVERED;
import static org.briarproject.bramble.api.sync.ValidationManager.State.INVALID;
import static org.briarproject.bramble.api.sync.ValidationManager.State.PENDING;
import static org.briarproject.bramble.api.sync.ValidationManager.State.UNKNOWN;
import static org.briarproject.bramble.db.DatabaseConstants.DB_SETTINGS_NAMESPACE;
import static org.briarproject.bramble.db.DatabaseConstants.MIN_SCHEMA_VERSION_KEY;
import static org.briarproject.bramble.db.DatabaseConstants.SCHEMA_VERSION_KEY;
import static org.briarproject.bramble.db.ExponentialBackoff.calculateExpiry;

/**
 * A generic database implementation that can be used with any JDBC-compatible
 * database library.
 */
@NotNullByDefault
abstract class JdbcDatabase implements Database<Connection> {

	private static final int SCHEMA_VERSION = 30;
	private static final int MIN_SCHEMA_VERSION = 30;

	private static final String CREATE_SETTINGS =
			"CREATE TABLE settings"
					+ " (namespace VARCHAR NOT NULL,"
					+ " key VARCHAR NOT NULL,"
					+ " value VARCHAR NOT NULL,"
					+ " PRIMARY KEY (namespace, key))";

	private static final String CREATE_LOCAL_AUTHORS =
			"CREATE TABLE localAuthors"
					+ " (authorId HASH NOT NULL,"
					+ " name VARCHAR NOT NULL,"
					+ " publicKey BINARY NOT NULL,"
					+ " privateKey BINARY NOT NULL,"
					+ " created BIGINT NOT NULL,"
					+ " PRIMARY KEY (authorId))";

	private static final String CREATE_CONTACTS =
			"CREATE TABLE contacts"
					+ " (contactId COUNTER,"
					+ " authorId HASH NOT NULL,"
					+ " name VARCHAR NOT NULL,"
					+ " publicKey BINARY NOT NULL,"
					+ " localAuthorId HASH NOT NULL,"
					+ " verified BOOLEAN NOT NULL,"
					+ " active BOOLEAN NOT NULL,"
					+ " PRIMARY KEY (contactId),"
					+ " FOREIGN KEY (localAuthorId)"
					+ " REFERENCES localAuthors (authorId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_GROUPS =
			"CREATE TABLE groups"
					+ " (groupId HASH NOT NULL,"
					+ " clientId VARCHAR NOT NULL,"
					+ " descriptor BINARY NOT NULL,"
					+ " PRIMARY KEY (groupId))";

	private static final String CREATE_GROUP_METADATA =
			"CREATE TABLE groupMetadata"
					+ " (groupId HASH NOT NULL,"
					+ " key VARCHAR NOT NULL,"
					+ " value BINARY NOT NULL,"
					+ " PRIMARY KEY (groupId, key),"
					+ " FOREIGN KEY (groupId)"
					+ " REFERENCES groups (groupId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_GROUP_VISIBILITIES =
			"CREATE TABLE groupVisibilities"
					+ " (contactId INT NOT NULL,"
					+ " groupId HASH NOT NULL,"
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
					+ " (messageId HASH NOT NULL,"
					+ " groupId HASH NOT NULL,"
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
					+ " (messageId HASH NOT NULL,"
					+ " key VARCHAR NOT NULL,"
					+ " value BINARY NOT NULL,"
					+ " PRIMARY KEY (messageId, key),"
					+ " FOREIGN KEY (messageId)"
					+ " REFERENCES messages (messageId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_MESSAGE_DEPENDENCIES =
			"CREATE TABLE messageDependencies"
					+ " (groupId HASH NOT NULL,"
					+ " messageId HASH NOT NULL,"
					+ " dependencyId HASH NOT NULL," // Not a foreign key
					+ " FOREIGN KEY (groupId)"
					+ " REFERENCES groups (groupId)"
					+ " ON DELETE CASCADE,"
					+ " FOREIGN KEY (messageId)"
					+ " REFERENCES messages (messageId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_OFFERS =
			"CREATE TABLE offers"
					+ " (messageId HASH NOT NULL," // Not a foreign key
					+ " contactId INT NOT NULL,"
					+ " PRIMARY KEY (messageId, contactId),"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_STATUSES =
			"CREATE TABLE statuses"
					+ " (messageId HASH NOT NULL,"
					+ " contactId INT NOT NULL,"
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
					+ " ON DELETE CASCADE)";

	private static final String CREATE_TRANSPORTS =
			"CREATE TABLE transports"
					+ " (transportId VARCHAR NOT NULL,"
					+ " maxLatency INT NOT NULL,"
					+ " PRIMARY KEY (transportId))";

	private static final String CREATE_INCOMING_KEYS =
			"CREATE TABLE incomingKeys"
					+ " (contactId INT NOT NULL,"
					+ " transportId VARCHAR NOT NULL,"
					+ " period BIGINT NOT NULL,"
					+ " tagKey SECRET NOT NULL,"
					+ " headerKey SECRET NOT NULL,"
					+ " base BIGINT NOT NULL,"
					+ " bitmap BINARY NOT NULL,"
					+ " PRIMARY KEY (contactId, transportId, period),"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE,"
					+ " FOREIGN KEY (transportId)"
					+ " REFERENCES transports (transportId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_OUTGOING_KEYS =
			"CREATE TABLE outgoingKeys"
					+ " (contactId INT NOT NULL,"
					+ " transportId VARCHAR NOT NULL,"
					+ " period BIGINT NOT NULL,"
					+ " tagKey SECRET NOT NULL,"
					+ " headerKey SECRET NOT NULL,"
					+ " stream BIGINT NOT NULL,"
					+ " PRIMARY KEY (contactId, transportId),"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE,"
					+ " FOREIGN KEY (transportId)"
					+ " REFERENCES transports (transportId)"
					+ " ON DELETE CASCADE)";

	private static final Logger LOG =
			Logger.getLogger(JdbcDatabase.class.getName());

	// Different database libraries use different names for certain types
	private final String hashType, binaryType, counterType, secretType;
	private final Clock clock;

	private final LinkedList<Connection> connections =
			new LinkedList<Connection>(); // Locking: connectionsLock

	private int openConnections = 0; // Locking: connectionsLock
	private boolean closed = false; // Locking: connectionsLock

	@Nullable
	protected abstract Connection createConnection() throws SQLException;

	private final Lock connectionsLock = new ReentrantLock();
	private final Condition connectionsChanged = connectionsLock.newCondition();

	JdbcDatabase(String hashType, String binaryType, String counterType,
			String secretType, Clock clock) {
		this.hashType = hashType;
		this.binaryType = binaryType;
		this.counterType = counterType;
		this.secretType = secretType;
		this.clock = clock;
	}

	protected void open(String driverClass, boolean reopen) throws DbException {
		// Load the JDBC driver
		try {
			Class.forName(driverClass);
		} catch (ClassNotFoundException e) {
			throw new DbException(e);
		}
		// Open the database and create the tables if necessary
		Connection txn = startTransaction();
		try {
			if (reopen) {
				if (!checkSchemaVersion(txn)) throw new DbException();
			} else {
				createTables(txn);
				storeSchemaVersion(txn);
			}
			commitTransaction(txn);
		} catch (DbException e) {
			abortTransaction(txn);
			throw e;
		}
	}

	private boolean checkSchemaVersion(Connection txn) throws DbException {
		Settings s = getSettings(txn, DB_SETTINGS_NAMESPACE);
		int schemaVersion = s.getInt(SCHEMA_VERSION_KEY, -1);
		if (schemaVersion == SCHEMA_VERSION) return true;
		if (schemaVersion < MIN_SCHEMA_VERSION) return false;
		int minSchemaVersion = s.getInt(MIN_SCHEMA_VERSION_KEY, -1);
		return SCHEMA_VERSION >= minSchemaVersion;
	}

	private void storeSchemaVersion(Connection txn) throws DbException {
		Settings s = new Settings();
		s.putInt(SCHEMA_VERSION_KEY, SCHEMA_VERSION);
		s.putInt(MIN_SCHEMA_VERSION_KEY, MIN_SCHEMA_VERSION);
		mergeSettings(txn, s, DB_SETTINGS_NAMESPACE);
	}

	private void tryToClose(@Nullable ResultSet rs) {
		try {
			if (rs != null) rs.close();
		} catch (SQLException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	private void tryToClose(@Nullable Statement s) {
		try {
			if (s != null) s.close();
		} catch (SQLException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
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
			s.executeUpdate(insertTypeNames(CREATE_INCOMING_KEYS));
			s.executeUpdate(insertTypeNames(CREATE_OUTGOING_KEYS));
			s.close();
		} catch (SQLException e) {
			tryToClose(s);
			throw new DbException(e);
		}
	}

	private String insertTypeNames(String s) {
		s = s.replaceAll("HASH", hashType);
		s = s.replaceAll("BINARY", binaryType);
		s = s.replaceAll("COUNTER", counterType);
		s = s.replaceAll("SECRET", secretType);
		return s;
	}

	@Override
	public Connection startTransaction() throws DbException {
		Connection txn = null;
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
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			try {
				txn.close();
			} catch (SQLException e1) {
				if (LOG.isLoggable(WARNING))
					LOG.log(WARNING, e1.toString(), e1);
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
					+ " (authorId, name, publicKey, localAuthorId, verified, active)"
					+ " VALUES (?, ?, ?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, remote.getId().getBytes());
			ps.setString(2, remote.getName());
			ps.setBytes(3, remote.getPublicKey());
			ps.setBytes(4, local.getBytes());
			ps.setBoolean(5, verified);
			ps.setBoolean(6, active);
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
			String sql = "INSERT INTO groups (groupId, clientId, descriptor)"
					+ " VALUES (?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getId().getBytes());
			ps.setString(2, g.getClientId().getString());
			ps.setBytes(3, g.getDescriptor());
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
			boolean shared) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO groupVisibilities"
					+ " (contactId, groupId, shared)"
					+ " VALUES (?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setBytes(2, g.getBytes());
			ps.setBoolean(3, shared);
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
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
					+ " (authorId, name, publicKey, privateKey, created)"
					+ " VALUES (?, ?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, a.getId().getBytes());
			ps.setString(2, a.getName());
			ps.setBytes(3, a.getPublicKey());
			ps.setBytes(4, a.getPrivateKey());
			ps.setLong(5, a.getTimeCreated());
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
			boolean shared) throws DbException {
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
			ps.setBoolean(5, shared);
			byte[] raw = m.getRaw();
			ps.setInt(6, raw.length);
			ps.setBytes(7, raw);
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
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

	@Override
	public void addStatus(Connection txn, ContactId c, MessageId m, boolean ack,
			boolean seen) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO statuses (messageId, contactId, ack,"
					+ " seen, requested, expiry, txCount)"
					+ " VALUES (?, ?, ?, ?, FALSE, 0, 0)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setInt(2, c.getInt());
			ps.setBoolean(3, ack);
			ps.setBoolean(4, seen);
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void addMessageDependency(Connection txn, GroupId g,
			MessageId dependent, MessageId dependency) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO messageDependencies"
					+ " (groupId, messageId, dependencyId)"
					+ " VALUES (?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			ps.setBytes(2, dependent.getBytes());
			ps.setBytes(3, dependency.getBytes());
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
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
	public void addTransportKeys(Connection txn, ContactId c, TransportKeys k)
			throws DbException {
		PreparedStatement ps = null;
		try {
			// Store the incoming keys
			String sql = "INSERT INTO incomingKeys (contactId, transportId,"
					+ " period, tagKey, headerKey, base, bitmap)"
					+ " VALUES (?, ?, ?, ?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setString(2, k.getTransportId().getString());
			// Previous rotation period
			IncomingKeys inPrev = k.getPreviousIncomingKeys();
			ps.setLong(3, inPrev.getRotationPeriod());
			ps.setBytes(4, inPrev.getTagKey().getBytes());
			ps.setBytes(5, inPrev.getHeaderKey().getBytes());
			ps.setLong(6, inPrev.getWindowBase());
			ps.setBytes(7, inPrev.getWindowBitmap());
			ps.addBatch();
			// Current rotation period
			IncomingKeys inCurr = k.getCurrentIncomingKeys();
			ps.setLong(3, inCurr.getRotationPeriod());
			ps.setBytes(4, inCurr.getTagKey().getBytes());
			ps.setBytes(5, inCurr.getHeaderKey().getBytes());
			ps.setLong(6, inCurr.getWindowBase());
			ps.setBytes(7, inCurr.getWindowBitmap());
			ps.addBatch();
			// Next rotation period
			IncomingKeys inNext = k.getNextIncomingKeys();
			ps.setLong(3, inNext.getRotationPeriod());
			ps.setBytes(4, inNext.getTagKey().getBytes());
			ps.setBytes(5, inNext.getHeaderKey().getBytes());
			ps.setLong(6, inNext.getWindowBase());
			ps.setBytes(7, inNext.getWindowBitmap());
			ps.addBatch();
			int[] batchAffected = ps.executeBatch();
			if (batchAffected.length != 3) throw new DbStateException();
			for (int rows : batchAffected)
				if (rows != 1) throw new DbStateException();
			ps.close();
			// Store the outgoing keys
			sql = "INSERT INTO outgoingKeys (contactId, transportId, period,"
					+ " tagKey, headerKey, stream)"
					+ " VALUES (?, ?, ?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setString(2, k.getTransportId().getString());
			OutgoingKeys outCurr = k.getCurrentOutgoingKeys();
			ps.setLong(3, outCurr.getRotationPeriod());
			ps.setBytes(4, outCurr.getTagKey().getBytes());
			ps.setBytes(5, outCurr.getHeaderKey().getBytes());
			ps.setLong(6, outCurr.getStreamCounter());
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
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
			String sql = "SELECT NULL FROM messages AS m"
					+ " JOIN groupVisibilities AS gv"
					+ " ON m.groupId = gv.groupId"
					+ " WHERE messageId = ?"
					+ " AND contactId = ?"
					+ " AND m.shared = TRUE";
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
			String sql = "SELECT authorId, name, publicKey,"
					+ " localAuthorId, verified, active"
					+ " FROM contacts"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			if (!rs.next()) throw new DbStateException();
			AuthorId authorId = new AuthorId(rs.getBytes(1));
			String name = rs.getString(2);
			byte[] publicKey = rs.getBytes(3);
			AuthorId localAuthorId = new AuthorId(rs.getBytes(4));
			boolean verified = rs.getBoolean(5);
			boolean active = rs.getBoolean(6);
			rs.close();
			ps.close();
			Author author = new Author(authorId, name, publicKey);
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
			String sql = "SELECT contactId, authorId, name, publicKey,"
					+ " localAuthorId, verified, active"
					+ " FROM contacts";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			List<Contact> contacts = new ArrayList<Contact>();
			while (rs.next()) {
				ContactId contactId = new ContactId(rs.getInt(1));
				AuthorId authorId = new AuthorId(rs.getBytes(2));
				String name = rs.getString(3);
				byte[] publicKey = rs.getBytes(4);
				Author author = new Author(authorId, name, publicKey);
				AuthorId localAuthorId = new AuthorId(rs.getBytes(5));
				boolean verified = rs.getBoolean(6);
				boolean active = rs.getBoolean(7);
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
			List<ContactId> ids = new ArrayList<ContactId>();
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
			String sql = "SELECT contactId, name, publicKey,"
					+ " localAuthorId, verified, active"
					+ " FROM contacts"
					+ " WHERE authorId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, remote.getBytes());
			rs = ps.executeQuery();
			List<Contact> contacts = new ArrayList<Contact>();
			while (rs.next()) {
				ContactId c = new ContactId(rs.getInt(1));
				String name = rs.getString(2);
				byte[] publicKey = rs.getBytes(3);
				AuthorId localAuthorId = new AuthorId(rs.getBytes(4));
				boolean verified = rs.getBoolean(5);
				boolean active = rs.getBoolean(6);
				Author author = new Author(remote, name, publicKey);
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
			String sql = "SELECT clientId, descriptor FROM groups"
					+ " WHERE groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			rs = ps.executeQuery();
			if (!rs.next()) throw new DbStateException();
			ClientId clientId = new ClientId(rs.getString(1));
			byte[] descriptor = rs.getBytes(2);
			rs.close();
			ps.close();
			return new Group(g, clientId, descriptor);
		} catch (SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public Collection<Group> getGroups(Connection txn, ClientId c)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT groupId, descriptor FROM groups"
					+ " WHERE clientId = ?";
			ps = txn.prepareStatement(sql);
			ps.setString(1, c.getString());
			rs = ps.executeQuery();
			List<Group> groups = new ArrayList<Group>();
			while (rs.next()) {
				GroupId id = new GroupId(rs.getBytes(1));
				byte[] descriptor = rs.getBytes(2);
				groups.add(new Group(id, c, descriptor));
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
	public Collection<ContactId> getGroupVisibility(Connection txn, GroupId g)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT contactId FROM groupVisibilities"
					+ " WHERE groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			rs = ps.executeQuery();
			List<ContactId> visible = new ArrayList<ContactId>();
			while (rs.next()) visible.add(new ContactId(rs.getInt(1)));
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
			String sql = "SELECT name, publicKey, privateKey, created"
					+ " FROM localAuthors"
					+ " WHERE authorId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, a.getBytes());
			rs = ps.executeQuery();
			if (!rs.next()) throw new DbStateException();
			String name = rs.getString(1);
			byte[] publicKey = rs.getBytes(2);
			byte[] privateKey = rs.getBytes(3);
			long created = rs.getLong(4);
			LocalAuthor localAuthor = new LocalAuthor(a, name, publicKey,
					privateKey, created);
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
			String sql = "SELECT authorId, name, publicKey, privateKey, created"
					+ " FROM localAuthors";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			List<LocalAuthor> authors = new ArrayList<LocalAuthor>();
			while (rs.next()) {
				AuthorId authorId = new AuthorId(rs.getBytes(1));
				String name = rs.getString(2);
				byte[] publicKey = rs.getBytes(3);
				byte[] privateKey = rs.getBytes(4);
				long created = rs.getLong(5);
				authors.add(new LocalAuthor(authorId, name, publicKey,
						privateKey, created));
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
			String sql = "SELECT messageId FROM messages WHERE groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			rs = ps.executeQuery();
			List<MessageId> ids = new ArrayList<MessageId>();
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

	private Collection<MessageId> getMessageIds(Connection txn, GroupId g,
			State state) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT messageId FROM messages"
					+ " WHERE state = ? AND groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, state.getValue());
			ps.setBytes(2, g.getBytes());
			rs = ps.executeQuery();
			List<MessageId> ids = new ArrayList<MessageId>();
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
		if (query.isEmpty()) return getMessageIds(txn, g, DELIVERED);
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Retrieve the message IDs for each query term and intersect
			Set<MessageId> intersection = null;
			String sql = "SELECT m.messageId"
					+ " FROM messages AS m"
					+ " JOIN messageMetadata AS md"
					+ " ON m.messageId = md.messageId"
					+ " WHERE state = ? AND groupId = ?"
					+ " AND key = ? AND value = ?";
			for (Entry<String, byte[]> e : query.entrySet()) {
				ps = txn.prepareStatement(sql);
				ps.setInt(1, DELIVERED.getValue());
				ps.setBytes(2, g.getBytes());
				ps.setString(3, e.getKey());
				ps.setBytes(4, e.getValue());
				rs = ps.executeQuery();
				Set<MessageId> ids = new HashSet<MessageId>();
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
			String sql = "SELECT m.messageId, key, value"
					+ " FROM messages AS m"
					+ " JOIN messageMetadata AS md"
					+ " ON m.messageId = md.messageId"
					+ " WHERE state = ? AND groupId = ?"
					+ " ORDER BY m.messageId";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, DELIVERED.getValue());
			ps.setBytes(2, g.getBytes());
			rs = ps.executeQuery();
			Map<MessageId, Metadata> all = new HashMap<MessageId, Metadata>();
			Metadata metadata = null;
			MessageId lastMessageId = null;
			while (rs.next()) {
				MessageId messageId = new MessageId(rs.getBytes(1));
				if (lastMessageId == null || !messageId.equals(lastMessageId)) {
					metadata = new Metadata();
					all.put(messageId, metadata);
					lastMessageId = messageId;
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
		Map<MessageId, Metadata> all = new HashMap<MessageId, Metadata>(
				matches.size());
		for (MessageId m : matches) all.put(m, getMessageMetadata(txn, m));
		return all;
	}

	@Override
	public Metadata getGroupMetadata(Connection txn, GroupId g)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT key, value FROM groupMetadata"
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
			String sql = "SELECT key, value FROM messageMetadata AS md"
					+ " JOIN messages AS m"
					+ " ON m.messageId = md.messageId"
					+ " WHERE m.state = ? AND md.messageId = ?";
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
			String sql = "SELECT key, value FROM messageMetadata AS md"
					+ " JOIN messages AS m"
					+ " ON m.messageId = md.messageId"
					+ " WHERE (m.state = ? OR m.state = ?)"
					+ " AND md.messageId = ?";
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
			String sql = "SELECT m.messageId, txCount > 0, seen"
					+ " FROM messages AS m"
					+ " JOIN statuses AS s"
					+ " ON m.messageId = s.messageId"
					+ " WHERE groupId = ?"
					+ " AND contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			ps.setInt(2, c.getInt());
			rs = ps.executeQuery();
			List<MessageStatus> statuses = new ArrayList<MessageStatus>();
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
	public MessageStatus getMessageStatus(Connection txn,
			ContactId c, MessageId m) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT txCount > 0, seen"
					+ " FROM statuses"
					+ " WHERE messageId = ?"
					+ " AND contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setInt(2, c.getInt());
			rs = ps.executeQuery();
			if (!rs.next()) throw new DbStateException();
			boolean sent = rs.getBoolean(1);
			boolean seen = rs.getBoolean(2);
			if (rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return new MessageStatus(m, c, sent, seen);
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
			String sql = "SELECT d.dependencyId, m.state, d.groupId, m.groupId"
					+ " FROM messageDependencies AS d"
					+ " LEFT OUTER JOIN messages AS m"
					+ " ON d.dependencyId = m.messageId"
					+ " WHERE d.messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			rs = ps.executeQuery();
			Map<MessageId, State> dependencies = new HashMap<MessageId, State>();
			while (rs.next()) {
				MessageId dependency = new MessageId(rs.getBytes(1));
				State state = State.fromValue(rs.getInt(2));
				if (rs.wasNull()) {
					state = UNKNOWN; // Missing dependency
				} else {
					GroupId dependentGroupId = new GroupId(rs.getBytes(3));
					GroupId dependencyGroupId = new GroupId(rs.getBytes(4));
					if (!dependentGroupId.equals(dependencyGroupId))
						state = INVALID; // Dependency in another group
				}
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
			String sql = "SELECT d.messageId, m.state"
					+ " FROM messageDependencies AS d"
					+ " JOIN messages AS m"
					+ " ON d.messageId = m.messageId"
					+ " WHERE dependencyId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			rs = ps.executeQuery();
			Map<MessageId, State> dependents = new HashMap<MessageId, State>();
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
			List<MessageId> ids = new ArrayList<MessageId>();
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
			String sql = "SELECT m.messageId FROM messages AS m"
					+ " JOIN groupVisibilities AS gv"
					+ " ON m.groupId = gv.groupId"
					+ " JOIN statuses AS s"
					+ " ON m.messageId = s.messageId"
					+ " AND gv.contactId = s.contactId"
					+ " WHERE gv.contactId = ? AND gv.shared = TRUE"
					+ " AND state = ? AND m.shared = TRUE AND raw IS NOT NULL"
					+ " AND seen = FALSE AND requested = FALSE"
					+ " AND expiry < ?"
					+ " ORDER BY timestamp LIMIT ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setInt(2, DELIVERED.getValue());
			ps.setLong(3, now);
			ps.setInt(4, maxMessages);
			rs = ps.executeQuery();
			List<MessageId> ids = new ArrayList<MessageId>();
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
			List<MessageId> ids = new ArrayList<MessageId>();
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
			String sql = "SELECT length, m.messageId FROM messages AS m"
					+ " JOIN groupVisibilities AS gv"
					+ " ON m.groupId = gv.groupId"
					+ " JOIN statuses AS s"
					+ " ON m.messageId = s.messageId"
					+ " AND gv.contactId = s.contactId"
					+ " WHERE gv.contactId = ? AND gv.shared = TRUE"
					+ " AND state = ? AND m.shared = TRUE AND raw IS NOT NULL"
					+ " AND seen = FALSE"
					+ " AND expiry < ?"
					+ " ORDER BY timestamp";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setInt(2, DELIVERED.getValue());
			ps.setLong(3, now);
			rs = ps.executeQuery();
			List<MessageId> ids = new ArrayList<MessageId>();
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
	public Collection<MessageId> getMessagesToValidate(Connection txn,
			ClientId c) throws DbException {
		return getMessagesInState(txn, c, UNKNOWN);
	}

	@Override
	public Collection<MessageId> getPendingMessages(Connection txn,
			ClientId c) throws DbException {
		return getMessagesInState(txn, c, PENDING);
	}

	private Collection<MessageId> getMessagesInState(Connection txn, ClientId c,
			State state) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT messageId FROM messages AS m"
					+ " JOIN groups AS g ON m.groupId = g.groupId"
					+ " WHERE state = ? AND clientId = ? AND raw IS NOT NULL";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, state.getValue());
			ps.setString(2, c.getString());
			rs = ps.executeQuery();
			List<MessageId> ids = new ArrayList<MessageId>();
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
	public Collection<MessageId> getMessagesToShare(
			Connection txn, ClientId c) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT m.messageId FROM messages AS m"
					+ " JOIN messageDependencies AS d"
					+ " ON m.messageId = d.dependencyId"
					+ " JOIN messages AS m1"
					+ " ON d.messageId = m1.messageId"
					+ " JOIN groups AS g"
					+ " ON m.groupId = g.groupId"
					+ " WHERE m.shared = FALSE AND m1.shared = TRUE"
					+ " AND g.clientId = ?";
			ps = txn.prepareStatement(sql);
			ps.setString(1, c.getString());
			rs = ps.executeQuery();
			List<MessageId> ids = new ArrayList<MessageId>();
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
			String sql = "SELECT length, m.messageId FROM messages AS m"
					+ " JOIN groupVisibilities AS gv"
					+ " ON m.groupId = gv.groupId"
					+ " JOIN statuses AS s"
					+ " ON m.messageId = s.messageId"
					+ " AND gv.contactId = s.contactId"
					+ " WHERE gv.contactId = ? AND gv.shared = TRUE"
					+ " AND state = ? AND m.shared = TRUE AND raw IS NOT NULL"
					+ " AND seen = FALSE AND requested = TRUE"
					+ " AND expiry < ?"
					+ " ORDER BY timestamp";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setInt(2, DELIVERED.getValue());
			ps.setLong(3, now);
			rs = ps.executeQuery();
			List<MessageId> ids = new ArrayList<MessageId>();
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
			String sql = "SELECT key, value FROM settings WHERE namespace = ?";
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
	public Map<ContactId, TransportKeys> getTransportKeys(Connection txn,
			TransportId t) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Retrieve the incoming keys
			String sql = "SELECT period, tagKey, headerKey, base, bitmap"
					+ " FROM incomingKeys"
					+ " WHERE transportId = ?"
					+ " ORDER BY contactId, period";
			ps = txn.prepareStatement(sql);
			ps.setString(1, t.getString());
			rs = ps.executeQuery();
			List<IncomingKeys> inKeys = new ArrayList<IncomingKeys>();
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
			sql = "SELECT contactId, period, tagKey, headerKey, stream"
					+ " FROM outgoingKeys"
					+ " WHERE transportId = ?"
					+ " ORDER BY contactId, period";
			ps = txn.prepareStatement(sql);
			ps.setString(1, t.getString());
			rs = ps.executeQuery();
			Map<ContactId, TransportKeys> keys =
					new HashMap<ContactId, TransportKeys>();
			for (int i = 0; rs.next(); i++) {
				// There should be three times as many incoming keys
				if (inKeys.size() < (i + 1) * 3) throw new DbStateException();
				ContactId contactId = new ContactId(rs.getInt(1));
				long rotationPeriod = rs.getLong(2);
				SecretKey tagKey = new SecretKey(rs.getBytes(3));
				SecretKey headerKey = new SecretKey(rs.getBytes(4));
				long streamCounter = rs.getLong(5);
				OutgoingKeys outCurr = new OutgoingKeys(tagKey, headerKey,
						rotationPeriod, streamCounter);
				IncomingKeys inPrev = inKeys.get(i * 3);
				IncomingKeys inCurr = inKeys.get(i * 3 + 1);
				IncomingKeys inNext = inKeys.get(i * 3 + 2);
				keys.put(contactId, new TransportKeys(t, inPrev, inCurr,
						inNext, outCurr));
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
	public void incrementStreamCounter(Connection txn, ContactId c,
			TransportId t, long rotationPeriod) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE outgoingKeys SET stream = stream + 1"
					+ " WHERE contactId = ? AND transportId = ? AND period = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setString(2, t.getString());
			ps.setLong(3, rotationPeriod);
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
			for (int rows: batchAffected) {
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
		mergeMetadata(txn, g.getBytes(), meta, "groupMetadata", "groupId");
	}

	@Override
	public void mergeMessageMetadata(Connection txn, MessageId m, Metadata meta)
			throws DbException {
		mergeMetadata(txn, m.getBytes(), meta, "messageMetadata", "messageId");
	}

	private void mergeMetadata(Connection txn, byte[] id, Metadata meta,
			String tableName, String columnName) throws DbException {
		PreparedStatement ps = null;
		try {
			// Determine which keys are being removed
			List<String> removed = new ArrayList<String>();
			Map<String, byte[]> retained = new HashMap<String, byte[]>();
			for (Entry<String, byte[]> e : meta.entrySet()) {
				if (e.getValue() == REMOVE) removed.add(e.getKey());
				else retained.put(e.getKey(), e.getValue());
			}
			// Delete any keys that are being removed
			if (!removed.isEmpty()) {
				String sql = "DELETE FROM " + tableName
						+ " WHERE " + columnName + " = ? AND key = ?";
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
			if (retained.isEmpty()) return;
			// Update any keys that already exist
			String sql = "UPDATE " + tableName + " SET value = ?"
					+ " WHERE " + columnName + " = ? AND key = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(2, id);
			for (Entry<String, byte[]> e : retained.entrySet()) {
				ps.setBytes(1, e.getValue());
				ps.setString(3, e.getKey());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if (batchAffected.length != retained.size())
				throw new DbStateException();
			for (int rows : batchAffected) {
				if (rows < 0) throw new DbStateException();
				if (rows > 1) throw new DbStateException();
			}
			// Insert any keys that don't already exist
			sql = "INSERT INTO " + tableName
					+ " (" + columnName + ", key, value)"
					+ " VALUES (?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, id);
			int updateIndex = 0, inserted = 0;
			for (Entry<String, byte[]> e : retained.entrySet()) {
				if (batchAffected[updateIndex] == 0) {
					ps.setString(2, e.getKey());
					ps.setBytes(3, e.getValue());
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
	public void mergeSettings(Connection txn, Settings s, String namespace)
			throws DbException {
		PreparedStatement ps = null;
		try {
			// Update any settings that already exist
			String sql = "UPDATE settings SET value = ?"
					+ " WHERE namespace = ? AND key = ?";
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
			sql = "INSERT INTO settings (namespace, key, value)"
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

	@Override
	public boolean removeOfferedMessage(Connection txn, ContactId c,
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
	public void removeStatus(Connection txn, ContactId c, MessageId m)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "DELETE FROM statuses"
					+ " WHERE contactId = ? AND messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setBytes(2, m.getBytes());
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
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
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void setMessageShared(Connection txn, MessageId m) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE messages SET shared = TRUE"
					+ " WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			int affected = ps.executeUpdate();
			if (affected < 0 || affected > 1) throw new DbStateException();
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
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	@Override
	public void setReorderingWindow(Connection txn, ContactId c, TransportId t,
			long rotationPeriod, long base, byte[] bitmap) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE incomingKeys SET base = ?, bitmap = ?"
					+ " WHERE contactId = ? AND transportId = ? AND period = ?";
			ps = txn.prepareStatement(sql);
			ps.setLong(1, base);
			ps.setBytes(2, bitmap);
			ps.setInt(3, c.getInt());
			ps.setString(4, t.getString());
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
	public void updateTransportKeys(Connection txn,
			Map<ContactId, TransportKeys> keys) throws DbException {
		PreparedStatement ps = null;
		try {
			// Delete any existing incoming keys
			String sql = "DELETE FROM incomingKeys"
					+ " WHERE contactId = ?"
					+ " AND transportId = ?";
			ps = txn.prepareStatement(sql);
			for (Entry<ContactId, TransportKeys> e : keys.entrySet()) {
				ps.setInt(1, e.getKey().getInt());
				ps.setString(2, e.getValue().getTransportId().getString());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if (batchAffected.length != keys.size())
				throw new DbStateException();
			ps.close();
			// Delete any existing outgoing keys
			sql = "DELETE FROM outgoingKeys"
					+ " WHERE contactId = ?"
					+ " AND transportId = ?";
			ps = txn.prepareStatement(sql);
			for (Entry<ContactId, TransportKeys> e : keys.entrySet()) {
				ps.setInt(1, e.getKey().getInt());
				ps.setString(2, e.getValue().getTransportId().getString());
				ps.addBatch();
			}
			batchAffected = ps.executeBatch();
			if (batchAffected.length != keys.size())
				throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
		// Store the new keys
		for (Entry<ContactId, TransportKeys> e : keys.entrySet()) {
			addTransportKeys(txn, e.getKey(), e.getValue());
		}
	}
}
