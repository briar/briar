package org.briarproject.db;

import static java.sql.Types.BINARY;
import static java.sql.Types.VARCHAR;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.Author.Status.ANONYMOUS;
import static org.briarproject.api.Author.Status.UNKNOWN;
import static org.briarproject.api.Author.Status.VERIFIED;
import static org.briarproject.api.messaging.MessagingConstants.MAX_SUBSCRIPTIONS;
import static org.briarproject.api.messaging.MessagingConstants.RETENTION_GRANULARITY;
import static org.briarproject.db.ExponentialBackoff.calculateExpiry;

import java.io.IOException;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.briarproject.api.Author;
import org.briarproject.api.AuthorId;
import org.briarproject.api.Contact;
import org.briarproject.api.ContactId;
import org.briarproject.api.LocalAuthor;
import org.briarproject.api.Settings;
import org.briarproject.api.TransportConfig;
import org.briarproject.api.TransportId;
import org.briarproject.api.TransportProperties;
import org.briarproject.api.db.DbClosedException;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.MessageHeader;
import org.briarproject.api.messaging.Group;
import org.briarproject.api.messaging.GroupId;
import org.briarproject.api.messaging.GroupStatus;
import org.briarproject.api.messaging.Message;
import org.briarproject.api.messaging.MessageId;
import org.briarproject.api.messaging.RetentionAck;
import org.briarproject.api.messaging.RetentionUpdate;
import org.briarproject.api.messaging.SubscriptionAck;
import org.briarproject.api.messaging.SubscriptionUpdate;
import org.briarproject.api.messaging.TransportAck;
import org.briarproject.api.messaging.TransportUpdate;
import org.briarproject.api.system.Clock;
import org.briarproject.api.transport.Endpoint;
import org.briarproject.api.transport.TemporarySecret;

/**
 * A generic database implementation that can be used with any JDBC-compatible
 * database library.
 */
abstract class JdbcDatabase implements Database<Connection> {

	private static final int SCHEMA_VERSION = 8;
	private static final int MIN_SCHEMA_VERSION = 8;

	private static final String CREATE_SETTINGS =
			"CREATE TABLE settings"
					+ " (key VARCHAR NOT NULL,"
					+ " value VARCHAR NOT NULL,"
					+ " PRIMARY KEY (key))";

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
					+ " PRIMARY KEY (contactId),"
					+ " UNIQUE (authorId),"
					+ " FOREIGN KEY (localAuthorId)"
					+ " REFERENCES localAuthors (authorId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_GROUPS =
			"CREATE TABLE groups"
					+ " (groupId HASH NOT NULL,"
					+ " name VARCHAR NOT NULL,"
					+ " salt BINARY NOT NULL,"
					+ " visibleToAll BOOLEAN NOT NULL,"
					+ " PRIMARY KEY (groupId))";

	private static final String CREATE_GROUP_VISIBILITIES =
			"CREATE TABLE groupVisibilities"
					+ " (contactId INT NOT NULL,"
					+ " groupId HASH NOT NULL,"
					+ " inbox BOOLEAN NOT NULL,"
					+ " PRIMARY KEY (contactId, groupId),"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE,"
					+ " FOREIGN KEY (groupId)"
					+ " REFERENCES groups (groupId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_CONTACT_GROUPS =
			"CREATE TABLE contactGroups"
					+ " (contactId INT NOT NULL,"
					+ " groupId HASH NOT NULL," // Not a foreign key
					+ " name VARCHAR NOT NULL,"
					+ " salt BINARY NOT NULL,"
					+ " PRIMARY KEY (contactId, groupId),"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_GROUP_VERSIONS =
			"CREATE TABLE groupVersions"
					+ " (contactId INT NOT NULL,"
					+ " localVersion BIGINT NOT NULL,"
					+ " localAcked BIGINT NOT NULL,"
					+ " remoteVersion BIGINT NOT NULL,"
					+ " remoteAcked BOOLEAN NOT NULL,"
					+ " expiry BIGINT NOT NULL,"
					+ " txCount INT NOT NULL,"
					+ " PRIMARY KEY (contactId),"
					+ " FOREIGN KEY (contactid)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_MESSAGES =
			"CREATE TABLE messages"
					+ " (messageId HASH NOT NULL,"
					+ " parentId HASH," // Null for the first msg in a thread
					+ " groupId HASH NOT NULL,"
					+ " authorId HASH," // Null for private/anon messages
					+ " authorName VARCHAR," // Null for private/anon messages
					+ " authorKey VARCHAR," // Null for private/anon messages
					+ " contentType VARCHAR NOT NULL,"
					+ " timestamp BIGINT NOT NULL,"
					+ " length INT NOT NULL,"
					+ " bodyStart INT NOT NULL,"
					+ " bodyLength INT NOT NULL,"
					+ " raw BLOB NOT NULL,"
					+ " local BOOLEAN NOT NULL,"
					+ " read BOOLEAN NOT NULL,"
					+ " PRIMARY KEY (messageId),"
					+ " FOREIGN KEY (groupId)"
					+ " REFERENCES groups (groupId)"
					+ " ON DELETE CASCADE)";

	private static final String INDEX_MESSAGES_BY_TIMESTAMP =
			"CREATE INDEX messagesByTimestamp ON messages (timestamp)";

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

	private static final String INDEX_STATUSES_BY_MESSAGE =
			"CREATE INDEX statusesByMessage ON statuses (messageId)";

	private static final String INDEX_STATUSES_BY_CONTACT =
			"CREATE INDEX statusesByContact ON statuses (contactId)";

	private static final String CREATE_RETENTION_VERSIONS =
			"CREATE TABLE retentionVersions"
					+ " (contactId INT NOT NULL,"
					+ " retention BIGINT NOT NULL,"
					+ " localVersion BIGINT NOT NULL,"
					+ " localAcked BIGINT NOT NULL,"
					+ " remoteVersion BIGINT NOT NULL,"
					+ " remoteAcked BOOLEAN NOT NULL,"
					+ " expiry BIGINT NOT NULL,"
					+ " txCount INT NOT NULL,"
					+ " PRIMARY KEY (contactId),"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_TRANSPORTS =
			"CREATE TABLE transports"
					+ " (transportId VARCHAR NOT NULL,"
					+ " maxLatency INT NOT NULL,"
					+ " PRIMARY KEY (transportId))";

	private static final String CREATE_TRANSPORT_CONFIGS =
			"CREATE TABLE transportConfigs"
					+ " (transportId VARCHAR NOT NULL,"
					+ " key VARCHAR NOT NULL,"
					+ " value VARCHAR NOT NULL,"
					+ " PRIMARY KEY (transportId, key),"
					+ " FOREIGN KEY (transportId)"
					+ " REFERENCES transports (transportId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_TRANSPORT_PROPS =
			"CREATE TABLE transportProperties"
					+ " (transportId VARCHAR NOT NULL,"
					+ " key VARCHAR NOT NULL,"
					+ " value VARCHAR NOT NULL,"
					+ " PRIMARY KEY (transportId, key),"
					+ " FOREIGN KEY (transportId)"
					+ " REFERENCES transports (transportId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_TRANSPORT_VERSIONS =
			"CREATE TABLE transportVersions"
					+ " (contactId INT NOT NULL,"
					+ " transportId VARCHAR NOT NULL,"
					+ " localVersion BIGINT NOT NULL,"
					+ " localAcked BIGINT NOT NULL,"
					+ " expiry BIGINT NOT NULL,"
					+ " txCount INT NOT NULL,"
					+ " PRIMARY KEY (contactId, transportId),"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE,"
					+ " FOREIGN KEY (transportId)"
					+ " REFERENCES transports (transportId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_CONTACT_TRANSPORT_PROPS =
			"CREATE TABLE contactTransportProperties"
					+ " (contactId INT NOT NULL,"
					+ " transportId VARCHAR NOT NULL," // Not a foreign key
					+ " key VARCHAR NOT NULL,"
					+ " value VARCHAR NOT NULL,"
					+ " PRIMARY KEY (contactId, transportId, key),"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_CONTACT_TRANSPORT_VERSIONS =
			"CREATE TABLE contactTransportVersions"
					+ " (contactId INT NOT NULL,"
					+ " transportId VARCHAR NOT NULL," // Not a foreign key
					+ " remoteVersion BIGINT NOT NULL,"
					+ " remoteAcked BOOLEAN NOT NULL,"
					+ " PRIMARY KEY (contactId, transportId),"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_ENDPOINTS =
			"CREATE TABLE endpoints"
					+ " (contactId INT NOT NULL,"
					+ " transportId VARCHAR NOT NULL,"
					+ " epoch BIGINT NOT NULL,"
					+ " alice BOOLEAN NOT NULL,"
					+ " PRIMARY KEY (contactId, transportId),"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE,"
					+ " FOREIGN KEY (transportId)"
					+ " REFERENCES transports (transportId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_SECRETS =
			"CREATE TABLE secrets"
					+ " (contactId INT NOT NULL,"
					+ " transportId VARCHAR NOT NULL,"
					+ " period BIGINT NOT NULL,"
					+ " secret SECRET NOT NULL,"
					+ " outgoing BIGINT NOT NULL,"
					+ " centre BIGINT NOT NULL,"
					+ " bitmap BINARY NOT NULL,"
					+ " PRIMARY KEY (contactId, transportId, period),"
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
			new LinkedList<Connection>(); // Locking: self

	private final AtomicInteger transactionCount = new AtomicInteger(0);

	private int openConnections = 0; // Locking: connections
	private boolean closed = false; // Locking: connections

	protected abstract Connection createConnection() throws SQLException;
	protected abstract void flushBuffersToDisk(Statement s) throws SQLException;

	JdbcDatabase(String hashType, String binaryType, String counterType,
			String secretType, Clock clock) {
		this.hashType = hashType;
		this.binaryType = binaryType;
		this.counterType = counterType;
		this.secretType = secretType;
		this.clock = clock;
	}

	protected void open(String driverClass, boolean reopen) throws DbException,
	IOException {
		// Load the JDBC driver
		try {
			Class.forName(driverClass);
		} catch(ClassNotFoundException e) {
			throw new DbException(e);
		}
		// Open the database and create the tables if necessary
		Connection txn = startTransaction();
		try {
			if(reopen) {
				if(!checkSchemaVersion(txn)) throw new DbException();
			} else {
				createTables(txn);
				Settings s = new Settings();
				s.put("schemaVersion", String.valueOf(SCHEMA_VERSION));
				s.put("minSchemaVersion", String.valueOf(MIN_SCHEMA_VERSION));
				mergeSettings(txn, s);
			}
			commitTransaction(txn);
		} catch(DbException e) {
			abortTransaction(txn);
			throw e;
		}
	}

	private boolean checkSchemaVersion(Connection txn) throws DbException {
		try {
			Settings s = getSettings(txn);
			int schemaVersion = Integer.valueOf(s.get("schemaVersion"));
			if(schemaVersion == SCHEMA_VERSION) return true;
			if(schemaVersion < MIN_SCHEMA_VERSION) return false;
			int minSchemaVersion = Integer.valueOf(s.get("minSchemaVersion"));
			return SCHEMA_VERSION >= minSchemaVersion;
		} catch(NumberFormatException e) {
			throw new DbException(e);
		}
	}

	private void tryToClose(ResultSet rs) {
		try {
			if(rs != null) rs.close();
		} catch(SQLException e) {
			if(LOG.isLoggable(WARNING))LOG.log(WARNING, e.toString(), e);
		}
	}

	private void tryToClose(Statement s) {
		try {
			if(s != null) s.close();
		} catch(SQLException e) {
			if(LOG.isLoggable(WARNING))LOG.log(WARNING, e.toString(), e);
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
			s.executeUpdate(insertTypeNames(CREATE_GROUP_VISIBILITIES));
			s.executeUpdate(insertTypeNames(CREATE_CONTACT_GROUPS));
			s.executeUpdate(insertTypeNames(CREATE_GROUP_VERSIONS));
			s.executeUpdate(insertTypeNames(CREATE_MESSAGES));
			s.executeUpdate(INDEX_MESSAGES_BY_TIMESTAMP);
			s.executeUpdate(insertTypeNames(CREATE_OFFERS));
			s.executeUpdate(insertTypeNames(CREATE_STATUSES));
			s.executeUpdate(INDEX_STATUSES_BY_MESSAGE);
			s.executeUpdate(INDEX_STATUSES_BY_CONTACT);
			s.executeUpdate(insertTypeNames(CREATE_RETENTION_VERSIONS));
			s.executeUpdate(insertTypeNames(CREATE_TRANSPORTS));
			s.executeUpdate(insertTypeNames(CREATE_TRANSPORT_CONFIGS));
			s.executeUpdate(insertTypeNames(CREATE_TRANSPORT_PROPS));
			s.executeUpdate(insertTypeNames(CREATE_TRANSPORT_VERSIONS));
			s.executeUpdate(insertTypeNames(CREATE_CONTACT_TRANSPORT_PROPS));
			s.executeUpdate(insertTypeNames(CREATE_CONTACT_TRANSPORT_VERSIONS));
			s.executeUpdate(insertTypeNames(CREATE_ENDPOINTS));
			s.executeUpdate(insertTypeNames(CREATE_SECRETS));
			s.close();
		} catch(SQLException e) {
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

	public Connection startTransaction() throws DbException {
		Connection txn = null;
		synchronized(connections) {
			if(closed) throw new DbClosedException();
			txn = connections.poll();
		}
		try {
			if(txn == null) {
				// Open a new connection
				txn = createConnection();
				if(txn == null) throw new DbException();
				txn.setAutoCommit(false);
				synchronized(connections) {
					openConnections++;
				}
			}
		} catch(SQLException e) {
			throw new DbException(e);
		}
		transactionCount.incrementAndGet();
		return txn;
	}

	public void abortTransaction(Connection txn) {
		try {
			txn.rollback();
			synchronized(connections) {
				connections.add(txn);
				connections.notifyAll();
			}
		} catch(SQLException e) {
			// Try to close the connection
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			try {
				txn.close();
			} catch(SQLException e1) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e1.toString(), e1);
			}
			// Whatever happens, allow the database to close
			synchronized(connections) {
				openConnections--;
				connections.notifyAll();
			}
		}
	}

	public void commitTransaction(Connection txn) throws DbException {
		Statement s = null;
		try {
			txn.commit();
			s = txn.createStatement();
			flushBuffersToDisk(s);
			s.close();
		} catch(SQLException e) {
			tryToClose(s);
			throw new DbException(e);
		}
		synchronized(connections) {
			connections.add(txn);
			connections.notifyAll();
		}
	}

	public int getTransactionCount() {
		return transactionCount.get();
	}

	public void resetTransactionCount() {
		transactionCount.set(0);
	}

	protected void closeAllConnections() throws SQLException {
		boolean interrupted = false;
		synchronized(connections) {
			closed = true;
			for(Connection c : connections) c.close();
			openConnections -= connections.size();
			connections.clear();
			while(openConnections > 0) {
				try {
					connections.wait();
				} catch(InterruptedException e) {
					LOG.warning("Interrupted while closing connections");
					interrupted = true;
				}
				for(Connection c : connections) c.close();
				openConnections -= connections.size();
				connections.clear();
			}
		}
		if(interrupted) Thread.currentThread().interrupt();
	}

	public ContactId addContact(Connection txn, Author remote, AuthorId local)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Create a contact row
			String sql = "INSERT INTO contacts"
					+ " (authorId, name, publicKey, localAuthorId)"
					+ " VALUES (?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, remote.getId().getBytes());
			ps.setString(2, remote.getName());
			ps.setBytes(3, remote.getPublicKey());
			ps.setBytes(4, local.getBytes());
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			// Get the new (highest) contact ID
			sql = "SELECT contactId FROM contacts"
					+ " ORDER BY contactId DESC LIMIT 1";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			if(!rs.next()) throw new DbStateException();
			ContactId c = new ContactId(rs.getInt(1));
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			// Create a status row for each message
			sql = "SELECT messageID FROM messages";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			Collection<byte[]> ids = new ArrayList<byte[]>();
			while(rs.next()) ids.add(rs.getBytes(1));
			rs.close();
			ps.close();
			if(!ids.isEmpty()) {
				sql = "INSERT INTO statuses (messageId, contactId, ack,"
						+ " seen, requested, expiry, txCount)"
						+ " VALUES (?, ?, FALSE, FALSE, FALSE, 0, 0)";
				ps = txn.prepareStatement(sql);
				ps.setInt(2, c.getInt());
				for(byte[] id : ids) {
					ps.setBytes(1, id);
					ps.addBatch();
				}
				int[] batchAffected = ps.executeBatch();
				if(batchAffected.length != ids.size())
					throw new DbStateException();
				for(int i = 0; i < batchAffected.length; i++) {
					if(batchAffected[i] != 1) throw new DbStateException();
				}
				ps.close();
			}
			// Make groups that are visible to everyone visible to this contact
			sql = "SELECT groupId FROM groups WHERE visibleToAll = TRUE";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			ids = new ArrayList<byte[]>();
			while(rs.next()) ids.add(rs.getBytes(1));
			rs.close();
			ps.close();
			if(!ids.isEmpty()) {
				sql = "INSERT INTO groupVisibilities"
						+ " (contactId, groupId, inbox)"
						+ " VALUES (?, ?, FALSE)";
				ps = txn.prepareStatement(sql);
				ps.setInt(1, c.getInt());
				for(byte[] id : ids) {
					ps.setBytes(2, id);
					ps.addBatch();
				}
				int[] batchAffected = ps.executeBatch();
				if(batchAffected.length != ids.size())
					throw new DbStateException();
				for(int i = 0; i < batchAffected.length; i++) {
					if(batchAffected[i] != 1) throw new DbStateException();
				}
				ps.close();
			}
			// Create a retention version row
			sql = "INSERT INTO retentionVersions (contactId, retention,"
					+ " localVersion, localAcked, remoteVersion, remoteAcked,"
					+ " expiry, txCount)"
					+ " VALUES (?, 0, 1, 0, 0, TRUE, 0, 0)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			// Create a group version row
			sql = "INSERT INTO groupVersions (contactId, localVersion,"
					+ " localAcked, remoteVersion, remoteAcked, expiry,"
					+ " txCount)"
					+ " VALUES (?, 1, 0, 0, TRUE, 0, 0)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			// Create a transport version row for each local transport
			sql = "SELECT transportId FROM transports";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			Collection<String> transports = new ArrayList<String>();
			while(rs.next()) transports.add(rs.getString(1));
			rs.close();
			ps.close();
			if(transports.isEmpty()) return c;
			sql = "INSERT INTO transportVersions (contactId, transportId,"
					+ " localVersion, localAcked, expiry, txCount)"
					+ " VALUES (?, ?, 1, 0, 0, 0)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			for(String t : transports) {
				ps.setString(2, t);
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if(batchAffected.length != transports.size())
				throw new DbStateException();
			for(int i = 0; i < batchAffected.length; i++) {
				if(batchAffected[i] != 1) throw new DbStateException();
			}
			ps.close();
			return c;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void addEndpoint(Connection txn, Endpoint ep) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO endpoints"
					+ " (contactId, transportId, epoch, alice)"
					+ " VALUES (?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, ep.getContactId().getInt());
			ps.setString(2, ep.getTransportId().getString());
			ps.setLong(3, ep.getEpoch());
			ps.setBoolean(4, ep.getAlice());
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public boolean addGroup(Connection txn, Group g) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT COUNT (groupId) FROM groups";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			if(!rs.next()) throw new DbStateException();
			int count = rs.getInt(1);
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			if(count > MAX_SUBSCRIPTIONS) throw new DbStateException();
			if(count == MAX_SUBSCRIPTIONS) return false;
			sql = "INSERT INTO groups"
					+ " (groupId, name, salt, visibleToAll)"
					+ " VALUES (?, ?, ?, FALSE)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getId().getBytes());
			ps.setString(2, g.getName());
			ps.setBytes(3, g.getSalt());
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			return true;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

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
			if(affected != 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void addMessage(Connection txn, Message m, boolean local)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO messages (messageId, parentId, groupId,"
					+ " authorId, authorName, authorKey, contentType,"
					+ " timestamp, length, bodyStart, bodyLength, raw,"
					+ " local, read)"
					+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getId().getBytes());
			if(m.getParent() == null) ps.setNull(2, BINARY);
			else ps.setBytes(2, m.getParent().getBytes());
			ps.setBytes(3, m.getGroup().getId().getBytes());
			Author a = m.getAuthor();
			if(a == null) {
				ps.setNull(4, BINARY);
				ps.setNull(5, VARCHAR);
				ps.setNull(6, BINARY);
			} else {
				ps.setBytes(4, a.getId().getBytes());
				ps.setString(5, a.getName());
				ps.setBytes(6, a.getPublicKey());
			}
			ps.setString(7, m.getContentType());
			ps.setLong(8, m.getTimestamp());
			byte[] raw = m.getSerialised();
			ps.setInt(9, raw.length);
			ps.setInt(10, m.getBodyStart());
			ps.setInt(11, m.getBodyLength());
			ps.setBytes(12, raw);
			ps.setBoolean(13, local);
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

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
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			if(found) return;
			sql = "INSERT INTO offers (messageId, contactId) VALUES (?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setInt(2, c.getInt());
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void addSecrets(Connection txn, Collection<TemporarySecret> secrets)
			throws DbException {
		PreparedStatement ps = null;
		try {
			// Store the new secrets
			String sql = "INSERT INTO secrets (contactId, transportId, period,"
					+ " secret, outgoing, centre, bitmap)"
					+ " VALUES (?, ?, ?, ?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			for(TemporarySecret s : secrets) {
				ps.setInt(1, s.getContactId().getInt());
				ps.setString(2, s.getTransportId().getString());
				ps.setLong(3, s.getPeriod());
				ps.setBytes(4, s.getSecret());
				ps.setLong(5, s.getOutgoingStreamCounter());
				ps.setLong(6, s.getWindowCentre());
				ps.setBytes(7, s.getWindowBitmap());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if(batchAffected.length != secrets.size())
				throw new DbStateException();
			for(int i = 0; i < batchAffected.length; i++) {
				if(batchAffected[i] != 1) throw new DbStateException();
			}
			ps.close();
			// Delete any obsolete secrets
			sql = "DELETE FROM secrets"
					+ " WHERE contactId = ? AND transportId = ? AND period < ?";
			ps = txn.prepareStatement(sql);
			for(TemporarySecret s : secrets) {
				ps.setInt(1, s.getContactId().getInt());
				ps.setString(2, s.getTransportId().getString());
				ps.setLong(3, s.getPeriod() - 2);
				ps.addBatch();
			}
			batchAffected = ps.executeBatch();
			if(batchAffected.length != secrets.size())
				throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

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
			if(affected != 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public boolean addTransport(Connection txn, TransportId t, int maxLatency)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Return false if the transport is already in the database
			String sql = "SELECT NULL FROM transports WHERE transportId = ?";
			ps = txn.prepareStatement(sql);
			ps.setString(1, t.getString());
			rs = ps.executeQuery();
			boolean found = rs.next();
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			if(found) return false;
			// Create a transport row
			sql = "INSERT INTO transports (transportId, maxLatency)"
					+ " VALUES (?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setString(1, t.getString());
			ps.setLong(2, maxLatency);
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			// Create a transport version row for each contact
			sql = "SELECT contactId FROM contacts";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			Collection<Integer> contacts = new ArrayList<Integer>();
			while(rs.next()) contacts.add(rs.getInt(1));
			rs.close();
			ps.close();
			if(contacts.isEmpty()) return true;
			sql = "INSERT INTO transportVersions (contactId, transportId,"
					+ " localVersion, localAcked, expiry, txCount)"
					+ " VALUES (?, ?, 1, 0, 0, 0)";
			ps = txn.prepareStatement(sql);
			ps.setString(2, t.getString());
			for(Integer c : contacts) {
				ps.setInt(1, c);
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if(batchAffected.length != contacts.size())
				throw new DbStateException();
			for(int i = 0; i < batchAffected.length; i++) {
				if(batchAffected[i] != 1) throw new DbStateException();
			}
			return true;
		} catch(SQLException e) {
			tryToClose(ps);
			tryToClose(rs);
			throw new DbException(e);
		}
	}

	public void addVisibility(Connection txn, ContactId c, GroupId g)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO groupVisibilities"
					+ " (contactId, groupId, inbox)"
					+ " VALUES (?, ?, FALSE)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setBytes(2, g.getBytes());
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			// Bump the subscription version
			sql = "UPDATE groupVersions"
					+ " SET localVersion = localVersion + 1,"
					+ " expiry = 0, txCount = 0"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public boolean containsContact(Connection txn, AuthorId a)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT NULL FROM contacts WHERE authorId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, a.getBytes());
			rs = ps.executeQuery();
			boolean found = rs.next();
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return found;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

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
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return found;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

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
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return found;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

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
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return found;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

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
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return found;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

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
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return found;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public boolean containsVisibleGroup(Connection txn, ContactId c, GroupId g)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT NULL FROM groupVisibilities"
					+ " WHERE contactId = ? AND groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setBytes(2, g.getBytes());
			rs = ps.executeQuery();
			boolean found = rs.next();
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return found;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public boolean containsVisibleMessage(Connection txn, ContactId c,
			MessageId m) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT NULL FROM messages AS m"
					+ " JOIN groupVisibilities AS gv"
					+ " ON m.groupId = gv.groupId"
					+ " WHERE messageId = ?"
					+ " AND contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setInt(2, c.getInt());
			rs = ps.executeQuery();
			boolean found = rs.next();
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return found;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

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
			if(!rs.next()) throw new DbException();
			int count = rs.getInt(1);
			if(rs.next()) throw new DbException();
			rs.close();
			ps.close();
			return count;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public Collection<GroupStatus> getAvailableGroups(Connection txn)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Add all subscribed groups to the list, except inbox groups
			String sql = "SELECT DISTINCT g.groupId, name, salt, visibleToAll"
					+ " FROM groups AS g"
					+ " LEFT OUTER JOIN groupVisibilities AS gv"
					+ " ON g.groupId = gv.groupId"
					+ " WHERE inbox = FALSE OR inbox IS NULL"
					+ " GROUP BY g.groupId";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			List<GroupStatus> groups = new ArrayList<GroupStatus>();
			Set<GroupId> ids = new HashSet<GroupId>();
			while(rs.next()) {
				GroupId id = new GroupId(rs.getBytes(1));
				if(!ids.add(id)) throw new DbStateException();
				String name = rs.getString(2);
				byte[] salt = rs.getBytes(3);
				Group group = new Group(id, name, salt);
				boolean visibleToAll = rs.getBoolean(4);
				groups.add(new GroupStatus(group, true, visibleToAll));
			}
			rs.close();
			ps.close();
			// Add all unsubscribed groups to the list
			sql = "SELECT DISTINCT cg.groupId, cg.name, cg.salt"
					+ " FROM contactGroups AS cg"
					+ " LEFT OUTER JOIN groups AS g"
					+ " ON cg.groupId = g.groupId"
					+ " WHERE g.groupId IS NULL"
					+ " GROUP BY cg.groupId";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			while(rs.next()) {
				GroupId id = new GroupId(rs.getBytes(1));
				if(!ids.add(id)) throw new DbStateException();
				String name = rs.getString(2);
				byte[] salt = rs.getBytes(3);
				Group group = new Group(id, name, salt);
				groups.add(new GroupStatus(group, false, false));
			}
			rs.close();
			ps.close();
			return Collections.unmodifiableList(groups);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public TransportConfig getConfig(Connection txn, TransportId t)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT key, value FROM transportConfigs"
					+ " WHERE transportId = ?";
			ps = txn.prepareStatement(sql);
			ps.setString(1, t.getString());
			rs = ps.executeQuery();
			TransportConfig c = new TransportConfig();
			while(rs.next()) c.put(rs.getString(1), rs.getString(2));
			rs.close();
			ps.close();
			return c;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public Contact getContact(Connection txn, ContactId c) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT authorId, name, publicKey, localAuthorId"
					+ " FROM contacts"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			if(!rs.next()) throw new DbStateException();
			AuthorId authorId = new AuthorId(rs.getBytes(1));
			String name = rs.getString(2);
			byte[] publicKey = rs.getBytes(3);
			AuthorId localAuthorId = new AuthorId(rs.getBytes(4));
			rs.close();
			ps.close();
			Author author = new Author(authorId, name, publicKey);
			return new Contact(c, author, localAuthorId);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public Collection<ContactId> getContactIds(Connection txn)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT contactId FROM contacts";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			List<ContactId> ids = new ArrayList<ContactId>();
			while(rs.next()) ids.add(new ContactId(rs.getInt(1)));
			rs.close();
			ps.close();
			return Collections.unmodifiableList(ids);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public Collection<Contact> getContacts(Connection txn)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT contactId, authorId, name, publicKey,"
					+ " localAuthorId"
					+ " FROM contacts";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			List<Contact> contacts = new ArrayList<Contact>();
			while(rs.next()) {
				ContactId contactId = new ContactId(rs.getInt(1));
				AuthorId authorId = new AuthorId(rs.getBytes(2));
				String name = rs.getString(3);
				byte[] publicKey = rs.getBytes(4);
				Author author = new Author(authorId, name, publicKey);
				AuthorId localAuthorId = new AuthorId(rs.getBytes(5));
				contacts.add(new Contact(contactId, author, localAuthorId));
			}
			rs.close();
			ps.close();
			return Collections.unmodifiableList(contacts);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public Collection<ContactId> getContacts(Connection txn, AuthorId a)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT contactId FROM contacts"
					+ " WHERE localAuthorId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, a.getBytes());
			rs = ps.executeQuery();
			List<ContactId> ids = new ArrayList<ContactId>();
			while(rs.next()) ids.add(new ContactId(rs.getInt(1)));
			rs.close();
			ps.close();
			return Collections.unmodifiableList(ids);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public Collection<Endpoint> getEndpoints(Connection txn)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT contactId, transportId, epoch, alice"
					+ " FROM endpoints";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			List<Endpoint> endpoints = new ArrayList<Endpoint>();
			while(rs.next()) {
				ContactId contactId = new ContactId(rs.getInt(1));
				TransportId transportId = new TransportId(rs.getString(2));
				long epoch = rs.getLong(3);
				boolean alice = rs.getBoolean(4);
				endpoints.add(new Endpoint(contactId, transportId, epoch,
						alice));
			}
			return Collections.unmodifiableList(endpoints);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public Group getGroup(Connection txn, GroupId g) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT name, salt FROM groups WHERE groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			rs = ps.executeQuery();
			if(!rs.next()) throw new DbStateException();
			String name = rs.getString(1);
			byte[] salt = rs.getBytes(2);
			rs.close();
			ps.close();
			return new Group(g, name, salt);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public Collection<Group> getGroups(Connection txn) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT groupId, name, salt FROM groups";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			List<Group> groups = new ArrayList<Group>();
			while(rs.next()) {
				GroupId id = new GroupId(rs.getBytes(1));
				String name = rs.getString(2);
				byte[] salt = rs.getBytes(3);
				groups.add(new Group(id, name, salt));
			}
			rs.close();
			ps.close();
			return Collections.unmodifiableList(groups);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public GroupId getInboxGroupId(Connection txn, ContactId c)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT groupId FROM groupVisibilities"
					+ " WHERE contactId = ?"
					+ " AND inbox = TRUE";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			GroupId inbox = null;
			if(rs.next()) inbox = new GroupId(rs.getBytes(1));
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return inbox;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public Collection<MessageHeader> getInboxMessageHeaders(Connection txn,
			ContactId c) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Get the local and remote authors
			String sql = "SELECT la.authorId, la.name, la.publicKey,"
					+ " c.authorId, c.name, c.publicKey"
					+ " FROM localAuthors AS la"
					+ " JOIN contacts AS c"
					+ " ON la.authorId = c.localAuthorId"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			if(!rs.next()) throw new DbException();
			AuthorId localId = new AuthorId(rs.getBytes(1));
			String localName = rs.getString(2);
			byte[] localKey = rs.getBytes(3);
			Author localAuthor = new Author(localId, localName, localKey);
			AuthorId remoteId = new AuthorId(rs.getBytes(4));
			String remoteName = rs.getString(5);
			byte[] remoteKey = rs.getBytes(6);
			Author remoteAuthor = new Author(remoteId, remoteName, remoteKey);
			if(rs.next()) throw new DbException();
			// Get the message headers
			sql = "SELECT m.messageId, parentId, m.groupId, contentType,"
					+ " timestamp, local, read, seen"
					+ " FROM messages AS m"
					+ " JOIN groups AS g"
					+ " ON m.groupId = g.groupId"
					+ " JOIN groupVisibilities AS gv"
					+ " ON m.groupId = gv.groupId"
					+ " JOIN statuses AS s"
					+ " ON m.messageId = s.messageId"
					+ " AND gv.contactId = s.contactId"
					+ " WHERE gv.contactId = ?"
					+ " AND inbox = TRUE";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			List<MessageHeader> headers = new ArrayList<MessageHeader>();
			while(rs.next()) {
				MessageId id = new MessageId(rs.getBytes(1));
				byte[] b = rs.getBytes(2);
				MessageId parent = b == null ? null : new MessageId(b);
				GroupId groupId = new GroupId(rs.getBytes(3));
				String contentType = rs.getString(4);
				long timestamp = rs.getLong(5);
				boolean local = rs.getBoolean(6);
				boolean read = rs.getBoolean(7);
				boolean seen = rs.getBoolean(8);
				Author author = local ? localAuthor : remoteAuthor;
				headers.add(new MessageHeader(id, parent, groupId, author,
						VERIFIED, contentType, timestamp, local, read, seen));
			}
			rs.close();
			ps.close();
			return Collections.unmodifiableList(headers);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

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
			if(!rs.next()) throw new DbStateException();
			String name = rs.getString(1);
			byte[] publicKey = rs.getBytes(2);
			byte[] privateKey = rs.getBytes(3);
			long created = rs.getLong(4);
			LocalAuthor localAuthor = new LocalAuthor(a, name, publicKey,
					privateKey, created);
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return localAuthor;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

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
			while(rs.next()) {
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
			return Collections.unmodifiableList(authors);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public Map<TransportId, TransportProperties> getLocalProperties(
			Connection txn) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT transportId, key, value"
					+ " FROM transportProperties"
					+ " ORDER BY transportId";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			Map<TransportId, TransportProperties> properties =
					new HashMap<TransportId, TransportProperties>();
			TransportId lastId = null;
			TransportProperties p = null;
			while(rs.next()) {
				TransportId id = new TransportId(rs.getString(1));
				String key = rs.getString(2), value = rs.getString(3);
				if(!id.equals(lastId)) {
					p = new TransportProperties();
					properties.put(id, p);
					lastId = id;
				}
				p.put(key, value);
			}
			rs.close();
			ps.close();
			return Collections.unmodifiableMap(properties);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public TransportProperties getLocalProperties(Connection txn, TransportId t)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT key, value FROM transportProperties"
					+ " WHERE transportId = ?";
			ps = txn.prepareStatement(sql);
			ps.setString(1, t.getString());
			rs = ps.executeQuery();
			TransportProperties p = new TransportProperties();
			while(rs.next()) p.put(rs.getString(1), rs.getString(2));
			rs.close();
			ps.close();
			return p;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public byte[] getMessageBody(Connection txn, MessageId m)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT bodyStart, bodyLength, raw FROM messages"
					+ " WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			rs = ps.executeQuery();
			if(!rs.next()) throw new DbStateException();
			int bodyStart = rs.getInt(1);
			int bodyLength = rs.getInt(2);
			// Bytes are indexed from 1 rather than 0
			byte[] body = rs.getBlob(3).getBytes(bodyStart + 1, bodyLength);
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return body;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public Collection<MessageHeader> getMessageHeaders(Connection txn,
			GroupId g) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT messageId, parentId, m.authorId, authorName,"
					+ " authorKey, contentType, timestamp, local, read,"
					+ " la.authorId IS NOT NULL, c.authorId IS NOT NULL"
					+ " FROM messages AS m"
					+ " LEFT OUTER JOIN localAuthors AS la"
					+ " ON m.authorId = la.authorId"
					+ " LEFT OUTER JOIN contacts AS c"
					+ " ON m.authorId = c.authorId"
					+ " WHERE groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			rs = ps.executeQuery();
			List<MessageHeader> headers = new ArrayList<MessageHeader>();
			while(rs.next()) {
				MessageId id = new MessageId(rs.getBytes(1));
				byte[] b = rs.getBytes(2);
				MessageId parent = b == null ? null : new MessageId(b);
				Author author;
				b = rs.getBytes(3);
				if(b == null) {
					author = null;
				} else {
					AuthorId authorId = new AuthorId(b);
					String authorName = rs.getString(4);
					byte[] authorKey = rs.getBytes(5);
					author = new Author(authorId, authorName, authorKey);
				}
				String contentType = rs.getString(6);
				long timestamp = rs.getLong(7);
				boolean local = rs.getBoolean(8);
				boolean read = rs.getBoolean(9);
				boolean isSelf = rs.getBoolean(10);
				boolean isContact = rs.getBoolean(11);
				Author.Status status;
				if(author == null) status = ANONYMOUS;
				else if(isSelf || isContact) status = VERIFIED;
				else status = UNKNOWN;
				headers.add(new MessageHeader(id, parent, g, author, status,
						contentType, timestamp, local, read, false));
			}
			rs.close();
			ps.close();
			return Collections.unmodifiableList(headers);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

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
			while(rs.next()) ids.add(new MessageId(rs.getBytes(1)));
			rs.close();
			ps.close();
			return Collections.unmodifiableList(ids);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public Collection<MessageId> getMessagesToOffer(Connection txn,
			ContactId c, int maxMessages) throws DbException {
		long now = clock.currentTimeMillis();
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT m.messageId FROM messages AS m"
					+ " JOIN contactGroups AS cg"
					+ " ON m.groupId = cg.groupId"
					+ " JOIN groupVisibilities AS gv"
					+ " ON m.groupId = gv.groupId"
					+ " AND cg.contactId = gv.contactId"
					+ " JOIN retentionVersions AS rv"
					+ " ON cg.contactId = rv.contactId"
					+ " JOIN statuses AS s"
					+ " ON m.messageId = s.messageId"
					+ " AND cg.contactId = s.contactId"
					+ " WHERE cg.contactId = ?"
					+ " AND timestamp >= retention"
					+ " AND seen = FALSE AND requested = FALSE"
					+ " AND s.expiry < ?"
					+ " ORDER BY timestamp DESC LIMIT ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setLong(2, now);
			ps.setInt(3, maxMessages);
			rs = ps.executeQuery();
			List<MessageId> ids = new ArrayList<MessageId>();
			while(rs.next()) ids.add(new MessageId(rs.getBytes(1)));
			rs.close();
			ps.close();
			return Collections.unmodifiableList(ids);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

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
			while(rs.next()) ids.add(new MessageId(rs.getBytes(1)));
			rs.close();
			ps.close();
			return Collections.unmodifiableList(ids);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public Collection<MessageId> getMessagesToSend(Connection txn, ContactId c,
			int maxLength) throws DbException {
		long now = clock.currentTimeMillis();
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT length, m.messageId FROM messages AS m"
					+ " JOIN contactGroups AS cg"
					+ " ON m.groupId = cg.groupId"
					+ " JOIN groupVisibilities AS gv"
					+ " ON m.groupId = gv.groupId"
					+ " AND cg.contactId = gv.contactId"
					+ " JOIN retentionVersions AS rv"
					+ " ON cg.contactId = rv.contactId"
					+ " JOIN statuses AS s"
					+ " ON m.messageId = s.messageId"
					+ " AND cg.contactId = s.contactId"
					+ " WHERE cg.contactId = ?"
					+ " AND timestamp >= retention"
					+ " AND seen = FALSE"
					+ " AND s.expiry < ?"
					+ " ORDER BY timestamp DESC";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setLong(2, now);
			rs = ps.executeQuery();
			List<MessageId> ids = new ArrayList<MessageId>();
			int total = 0;
			while(rs.next()) {
				int length = rs.getInt(1);
				if(total + length > maxLength) break;
				ids.add(new MessageId(rs.getBytes(2)));
				total += length;
			}
			rs.close();
			ps.close();
			return Collections.unmodifiableList(ids);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public Collection<MessageId> getOldMessages(Connection txn, int capacity)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT length, messageId FROM messages"
					+ " ORDER BY timestamp";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			List<MessageId> ids = new ArrayList<MessageId>();
			int total = 0;
			while(rs.next()) {
				int length = rs.getInt(1);
				if(total + length > capacity) break;
				ids.add(new MessageId(rs.getBytes(2)));
				total += length;
			}
			rs.close();
			ps.close();
			return Collections.unmodifiableList(ids);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public MessageId getParent(Connection txn, MessageId m) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT m1.parentId FROM messages AS m1"
					+ " JOIN messages AS m2"
					+ " ON m1.parentId = m2.messageId"
					+ " AND m1.groupId = m2.groupId"
					+ " WHERE m1.messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			rs = ps.executeQuery();
			MessageId parent = null;
			if(rs.next()) {
				parent = new MessageId(rs.getBytes(1));
				if(rs.next()) throw new DbStateException();
			}
			rs.close();
			ps.close();
			return parent;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public byte[] getRawMessage(Connection txn, MessageId m)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT length, raw FROM messages WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			rs = ps.executeQuery();
			if(!rs.next()) throw new DbStateException();
			int length = rs.getInt(1);
			byte[] raw = rs.getBlob(2).getBytes(1, length);
			if(raw.length != length) throw new DbStateException();
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return raw;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public boolean getReadFlag(Connection txn, MessageId m) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT read FROM messages WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			rs = ps.executeQuery();
			if(!rs.next()) throw new DbStateException();
			boolean read = rs.getBoolean(1);
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return read;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public Map<ContactId, TransportProperties> getRemoteProperties(
			Connection txn, TransportId t) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT contactId, key, value"
					+ " FROM contactTransportProperties"
					+ " WHERE transportId = ?"
					+ " ORDER BY contactId";
			ps = txn.prepareStatement(sql);
			ps.setString(1, t.getString());
			rs = ps.executeQuery();
			Map<ContactId, TransportProperties> properties =
					new HashMap<ContactId, TransportProperties>();
			ContactId lastId = null;
			TransportProperties p = null;
			while(rs.next()) {
				ContactId id = new ContactId(rs.getInt(1));
				String key = rs.getString(2), value = rs.getString(3);
				if(!id.equals(lastId)) {
					p = new TransportProperties();
					properties.put(id, p);
					lastId = id;
				}
				p.put(key, value);
			}
			rs.close();
			ps.close();
			return Collections.unmodifiableMap(properties);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public Collection<MessageId> getRequestedMessagesToSend(Connection txn,
			ContactId c, int maxLength) throws DbException {
		long now = clock.currentTimeMillis();
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT length, m.messageId FROM messages AS m"
					+ " JOIN contactGroups AS cg"
					+ " ON m.groupId = cg.groupId"
					+ " JOIN groupVisibilities AS gv"
					+ " ON m.groupId = gv.groupId"
					+ " AND cg.contactId = gv.contactId"
					+ " JOIN retentionVersions AS rv"
					+ " ON cg.contactId = rv.contactId"
					+ " JOIN statuses AS s"
					+ " ON m.messageId = s.messageId"
					+ " AND cg.contactId = s.contactId"
					+ " WHERE cg.contactId = ?"
					+ " AND timestamp >= retention"
					+ " AND seen = FALSE AND requested = TRUE"
					+ " AND s.expiry < ?"
					+ " ORDER BY timestamp DESC";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setLong(2, now);
			rs = ps.executeQuery();
			List<MessageId> ids = new ArrayList<MessageId>();
			int total = 0;
			while(rs.next()) {
				int length = rs.getInt(1);
				if(total + length > maxLength) break;
				ids.add(new MessageId(rs.getBytes(2)));
				total += length;
			}
			rs.close();
			ps.close();
			return Collections.unmodifiableList(ids);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public RetentionAck getRetentionAck(Connection txn, ContactId c)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT remoteVersion FROM retentionVersions"
					+ " WHERE contactId = ? AND remoteAcked = FALSE";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			if(!rs.next()) {
				rs.close();
				ps.close();
				return null;
			}
			long version = rs.getLong(1);
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			sql = "UPDATE retentionVersions SET remoteAcked = TRUE"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			return new RetentionAck(version);
		} catch(SQLException e) {
			tryToClose(ps);
			tryToClose(rs);
			throw new DbException(e);
		}
	}

	public RetentionUpdate getRetentionUpdate(Connection txn, ContactId c,
			int maxLatency) throws DbException {
		long now = clock.currentTimeMillis();
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT localVersion, txCount"
					+ " FROM retentionVersions"
					+ " WHERE contactId = ?"
					+ " AND localVersion > localAcked"
					+ " AND expiry < ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setLong(2, now);
			rs = ps.executeQuery();
			if(!rs.next()) {
				rs.close();
				ps.close();
				return null;
			}
			long version = rs.getLong(1);
			int txCount = rs.getInt(2);
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			sql = "SELECT timestamp FROM messages AS m"
					+ " ORDER BY timestamp LIMIT 1";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			long retention = 0;
			if(rs.next()) {
				retention = rs.getLong(1);
				retention -= retention % RETENTION_GRANULARITY;
			}
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			sql = "UPDATE retentionVersions"
					+ " SET expiry = ?, txCount = txCount + 1"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setLong(1, calculateExpiry(now, maxLatency, txCount));
			ps.setInt(2, c.getInt());
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			return new RetentionUpdate(retention, version);
		} catch(SQLException e) {
			tryToClose(ps);
			tryToClose(rs);
			throw new DbException(e);
		}
	}

	public Collection<TemporarySecret> getSecrets(Connection txn)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT e.contactId, e.transportId, epoch, alice,"
					+ " period, secret, outgoing, centre, bitmap"
					+ " FROM endpoints AS e"
					+ " JOIN secrets AS s"
					+ " ON e.contactId = s.contactId"
					+ " AND e.transportId = s.transportId";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			List<TemporarySecret> secrets = new ArrayList<TemporarySecret>();
			while(rs.next()) {
				ContactId contactId = new ContactId(rs.getInt(1));
				TransportId transportId = new TransportId(rs.getString(2));
				long epoch = rs.getLong(3);
				boolean alice = rs.getBoolean(4);
				long period = rs.getLong(5);
				byte[] secret = rs.getBytes(6);
				long outgoing = rs.getLong(7);
				long centre = rs.getLong(8);
				byte[] bitmap = rs.getBytes(9);
				secrets.add(new TemporarySecret(contactId, transportId, epoch,
						alice, period, secret, outgoing, centre, bitmap));
			}
			rs.close();
			ps.close();
			return Collections.unmodifiableList(secrets);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public Settings getSettings(Connection txn) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT key, value FROM settings";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			Settings s = new Settings();
			while(rs.next()) s.put(rs.getString(1), rs.getString(2));
			rs.close();
			ps.close();
			return s;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public Collection<Contact> getSubscribers(Connection txn, GroupId g)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT c.contactId, authorId, c.name, publicKey,"
					+ " localAuthorId"
					+ " FROM contacts AS c"
					+ " JOIN contactGroups AS cg"
					+ " ON c.contactId = cg.contactId"
					+ " WHERE groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			rs = ps.executeQuery();
			List<Contact> contacts = new ArrayList<Contact>();
			while(rs.next()) {
				ContactId contactId = new ContactId(rs.getInt(1));
				AuthorId authorId = new AuthorId(rs.getBytes(2));
				String name = rs.getString(3);
				byte[] publicKey = rs.getBytes(4);
				Author author = new Author(authorId, name, publicKey);
				AuthorId localAuthorId = new AuthorId(rs.getBytes(5));
				contacts.add(new Contact(contactId, author, localAuthorId));
			}
			rs.close();
			ps.close();
			return Collections.unmodifiableList(contacts);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public SubscriptionAck getSubscriptionAck(Connection txn, ContactId c)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT remoteVersion FROM groupVersions"
					+ " WHERE contactId = ? AND remoteAcked = FALSE";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			if(!rs.next()) {
				rs.close();
				ps.close();
				return null;
			}
			long version = rs.getLong(1);
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			sql = "UPDATE groupVersions SET remoteAcked = TRUE"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			return new SubscriptionAck(version);
		} catch(SQLException e) {
			tryToClose(ps);
			tryToClose(rs);
			throw new DbException(e);
		}
	}

	public SubscriptionUpdate getSubscriptionUpdate(Connection txn, ContactId c,
			int maxLatency) throws DbException {
		long now = clock.currentTimeMillis();
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT g.groupId, name, salt, localVersion, txCount"
					+ " FROM groups AS g"
					+ " JOIN groupVisibilities AS gvis"
					+ " ON g.groupId = gvis.groupId"
					+ " JOIN groupVersions AS gver"
					+ " ON gvis.contactId = gver.contactId"
					+ " WHERE gvis.contactId = ?"
					+ " AND localVersion > localAcked"
					+ " AND expiry < ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setLong(2, now);
			rs = ps.executeQuery();
			List<Group> groups = new ArrayList<Group>();
			Set<GroupId> ids = new HashSet<GroupId>();
			long version = 0;
			int txCount = 0;
			while(rs.next()) {
				GroupId id = new GroupId(rs.getBytes(1));
				if(!ids.add(id)) throw new DbStateException();
				String name = rs.getString(2);
				byte[] salt = rs.getBytes(3);
				groups.add(new Group(id, name, salt));
				version = rs.getLong(4);
				txCount = rs.getInt(5);
			}
			rs.close();
			ps.close();
			if(groups.isEmpty()) return null;
			sql = "UPDATE groupVersions"
					+ " SET expiry = ?, txCount = txCount + 1"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setLong(1, calculateExpiry(now, maxLatency, txCount));
			ps.setInt(2, c.getInt());
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			groups = Collections.unmodifiableList(groups);
			return new SubscriptionUpdate(groups, version);
		} catch(SQLException e) {
			tryToClose(ps);
			tryToClose(rs);
			throw new DbException(e);
		}
	}

	public Collection<TransportAck> getTransportAcks(Connection txn,
			ContactId c) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT transportId, remoteVersion"
					+ " FROM contactTransportVersions"
					+ " WHERE contactId = ? AND remoteAcked = FALSE";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			List<TransportAck> acks = new ArrayList<TransportAck>();
			while(rs.next()) {
				TransportId id = new TransportId(rs.getString(1));
				acks.add(new TransportAck(id, rs.getLong(2)));
			}
			rs.close();
			ps.close();
			if(acks.isEmpty()) return null;
			sql = "UPDATE contactTransportVersions SET remoteAcked = TRUE"
					+ " WHERE contactId = ? AND transportId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			for(TransportAck a : acks) {
				ps.setString(2, a.getId().getString());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if(batchAffected.length != acks.size())
				throw new DbStateException();
			for(int i = 0; i < batchAffected.length; i++) {
				if(batchAffected[i] != 1) throw new DbStateException();
			}
			ps.close();
			return Collections.unmodifiableList(acks);
		} catch(SQLException e) {
			tryToClose(ps);
			tryToClose(rs);
			throw new DbException(e);
		}
	}

	public Map<TransportId, Integer> getTransportLatencies(Connection txn)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT transportId, maxLatency FROM transports";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			Map<TransportId, Integer> latencies =
					new HashMap<TransportId, Integer>();
			while(rs.next()){
				TransportId id = new TransportId(rs.getString(1));
				latencies.put(id, rs.getInt(2));
			}
			rs.close();
			ps.close();
			return Collections.unmodifiableMap(latencies);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public Collection<TransportUpdate> getTransportUpdates(Connection txn,
			ContactId c, int maxLatency) throws DbException {
		long now = clock.currentTimeMillis();
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT tp.transportId, key, value, localVersion,"
					+ " txCount"
					+ " FROM transportProperties AS tp"
					+ " JOIN transportVersions AS tv"
					+ " ON tp.transportId = tv.transportId"
					+ " WHERE tv.contactId = ?"
					+ " AND localVersion > localAcked"
					+ " AND expiry < ?"
					+ " ORDER BY tp.transportId";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setLong(2, now);
			rs = ps.executeQuery();
			List<TransportUpdate> updates = new ArrayList<TransportUpdate>();
			TransportId lastId = null;
			TransportProperties p = null;
			List<Integer> txCounts = new ArrayList<Integer>();
			while(rs.next()) {
				TransportId id = new TransportId(rs.getString(1));
				String key = rs.getString(2), value = rs.getString(3);
				long version = rs.getLong(4);
				int txCount = rs.getInt(5);
				if(!id.equals(lastId)) {
					p = new TransportProperties();
					updates.add(new TransportUpdate(id, p, version));
					txCounts.add(txCount);
					lastId = id;
				}
				p.put(key, value);
			}
			rs.close();
			ps.close();
			if(updates.isEmpty()) return null;
			sql = "UPDATE transportVersions"
					+ " SET expiry = ?, txCount = txCount + 1"
					+ " WHERE contactId = ? AND transportId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(2, c.getInt());
			int i = 0;
			for(TransportUpdate u : updates) {
				int txCount = txCounts.get(i++);
				ps.setLong(1, calculateExpiry(now, maxLatency, txCount));
				ps.setString(3, u.getId().getString());
				ps.addBatch();
			}
			int [] batchAffected = ps.executeBatch();
			if(batchAffected.length != updates.size())
				throw new DbStateException();
			for(i = 0; i < batchAffected.length; i++) {
				if(batchAffected[i] != 1) throw new DbStateException();
			}
			ps.close();
			return Collections.unmodifiableList(updates);
		} catch(SQLException e) {
			tryToClose(ps);
			tryToClose(rs);
			throw new DbException(e);
		}
	}

	public Map<GroupId, Integer> getUnreadMessageCounts(Connection txn)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT groupId, COUNT(*)"
					+ " FROM messages AS m"
					+ " WHERE read = FALSE"
					+ " GROUP BY groupId";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			Map<GroupId, Integer> counts = new HashMap<GroupId, Integer>();
			while(rs.next()) {
				GroupId groupId = new GroupId(rs.getBytes(1));
				counts.put(groupId, rs.getInt(2));
			}
			rs.close();
			ps.close();
			return Collections.unmodifiableMap(counts);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public Collection<ContactId> getVisibility(Connection txn, GroupId g)
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
			while(rs.next()) visible.add(new ContactId(rs.getInt(1)));
			rs.close();
			ps.close();
			return Collections.unmodifiableList(visible);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public long incrementStreamCounter(Connection txn, ContactId c,
			TransportId t, long period) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Get the current stream counter
			String sql = "SELECT outgoing FROM secrets"
					+ " WHERE contactId = ? AND transportId = ? AND period = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setString(2, t.getString());
			ps.setLong(3, period);
			rs = ps.executeQuery();
			if(!rs.next()) {
				rs.close();
				ps.close();
				return -1;
			}
			long streamNumber = rs.getLong(1);
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			// Increment the stream counter
			sql = "UPDATE secrets SET outgoing = outgoing + 1"
					+ " WHERE contactId = ? AND transportId = ? AND period = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setString(2, t.getString());
			ps.setLong(3, period);
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			return streamNumber;
		} catch(SQLException e) {
			tryToClose(ps);
			tryToClose(rs);
			throw new DbException(e);
		}
	}

	public void incrementRetentionVersions(Connection txn) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE retentionVersions"
					+ " SET localVersion = localVersion + 1, expiry = 0";
			ps = txn.prepareStatement(sql);
			ps.executeUpdate();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void lowerAckFlag(Connection txn, ContactId c,
			Collection<MessageId> acked) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE statuses SET ack = FALSE"
					+ " WHERE messageId = ? AND contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(2, c.getInt());
			for(MessageId m : acked) {
				ps.setBytes(1, m.getBytes());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if(batchAffected.length != acked.size())
				throw new DbStateException();
			for(int i = 0; i < batchAffected.length; i++) {
				if(batchAffected[i] < 0) throw new DbStateException();
				if(batchAffected[i] > 1) throw new DbStateException();
			}
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void lowerRequestedFlag(Connection txn, ContactId c,
			Collection<MessageId> requested) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE statuses SET requested = FALSE"
					+ " WHERE messageId = ? AND contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(2, c.getInt());
			for(MessageId m : requested) {
				ps.setBytes(1, m.getBytes());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if(batchAffected.length != requested.size())
				throw new DbStateException();
			for(int i = 0; i < batchAffected.length; i++) {
				if(batchAffected[i] < 0) throw new DbStateException();
				if(batchAffected[i] > 1) throw new DbStateException();
			}
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void mergeConfig(Connection txn, TransportId t, TransportConfig c)
			throws DbException {
		// Merge the new configuration with the existing one
		mergeStringMap(txn, t, c, "transportConfigs");
	}

	public void mergeLocalProperties(Connection txn, TransportId t,
			TransportProperties p) throws DbException {
		// Merge the new properties with the existing ones
		mergeStringMap(txn, t, p, "transportProperties");
		// Bump the transport version
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE transportVersions"
					+ " SET localVersion = localVersion + 1, expiry = 0"
					+ " WHERE transportId = ?";
			ps = txn.prepareStatement(sql);
			ps.setString(1, t.getString());
			ps.executeUpdate();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	private void mergeStringMap(Connection txn, TransportId t,
			Map<String, String> m, String tableName) throws DbException {
		PreparedStatement ps = null;
		try {
			// Update any properties that already exist
			String sql = "UPDATE " + tableName + " SET value = ?"
					+ " WHERE transportId = ? AND key = ?";
			ps = txn.prepareStatement(sql);
			ps.setString(2, t.getString());
			for(Entry<String, String> e : m.entrySet()) {
				ps.setString(1, e.getValue());
				ps.setString(3, e.getKey());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if(batchAffected.length != m.size()) throw new DbStateException();
			for(int i = 0; i < batchAffected.length; i++) {
				if(batchAffected[i] < 0) throw new DbStateException();
				if(batchAffected[i] > 1) throw new DbStateException();
			}
			// Insert any properties that don't already exist
			sql = "INSERT INTO " + tableName + " (transportId, key, value)"
					+ " VALUES (?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setString(1, t.getString());
			int updateIndex = 0, inserted = 0;
			for(Entry<String, String> e : m.entrySet()) {
				if(batchAffected[updateIndex] == 0) {
					ps.setString(2, e.getKey());
					ps.setString(3, e.getValue());
					ps.addBatch();
					inserted++;
				}
				updateIndex++;
			}
			batchAffected = ps.executeBatch();
			if(batchAffected.length != inserted) throw new DbStateException();
			for(int i = 0; i < batchAffected.length; i++) {
				if(batchAffected[i] != 1) throw new DbStateException();
			}
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void mergeSettings(Connection txn, Settings s) throws DbException {
		PreparedStatement ps = null;
		try {
			// Update any settings that already exist
			String sql = "UPDATE settings SET value = ? WHERE key = ?";
			ps = txn.prepareStatement(sql);
			for(Entry<String, String> e : s.entrySet()) {
				ps.setString(1, e.getValue());
				ps.setString(2, e.getKey());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if(batchAffected.length != s.size()) throw new DbStateException();
			for(int i = 0; i < batchAffected.length; i++) {
				if(batchAffected[i] < 0) throw new DbStateException();
				if(batchAffected[i] > 1) throw new DbStateException();
			}
			// Insert any settings that don't already exist
			sql = "INSERT INTO settings (key, value) VALUES (?, ?)";
			ps = txn.prepareStatement(sql);
			int updateIndex = 0, inserted = 0;
			for(Entry<String, String> e : s.entrySet()) {
				if(batchAffected[updateIndex] == 0) {
					ps.setString(1, e.getKey());
					ps.setString(2, e.getValue());
					ps.addBatch();
					inserted++;
				}
				updateIndex++;
			}
			batchAffected = ps.executeBatch();
			if(batchAffected.length != inserted) throw new DbStateException();
			for(int i = 0; i < batchAffected.length; i++) {
				if(batchAffected[i] != 1) throw new DbStateException();
			}
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

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
			if(affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void raiseRequestedFlag(Connection txn, ContactId c, MessageId m)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "UPDATE statuses SET requested = TRUE"
					+ " WHERE messageId = ? AND contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setInt(2, c.getInt());
			int affected = ps.executeUpdate();
			if(affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void raiseSeenFlag(Connection txn, ContactId c, MessageId m)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "UPDATE statuses SET seen = TRUE"
					+ " WHERE messageId = ? AND contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setInt(2, c.getInt());
			int affected = ps.executeUpdate();
			if(affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void removeContact(Connection txn, ContactId c)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "DELETE FROM contacts WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void removeGroup(Connection txn, GroupId g) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Find out which contacts are affected
			String sql = "SELECT contactId FROM groupVisibilities"
					+ " WHERE groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			rs = ps.executeQuery();
			Collection<Integer> visible = new ArrayList<Integer>();
			while(rs.next()) visible.add(rs.getInt(1));
			rs.close();
			ps.close();
			// Delete the group
			sql = "DELETE FROM groups WHERE groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			if(visible.isEmpty()) return;
			// Bump the subscription versions for the affected contacts
			sql = "UPDATE groupVersions"
					+ " SET localVersion = localVersion + 1, expiry = 0"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			for(Integer c : visible) {
				ps.setInt(1, c);
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if(batchAffected.length != visible.size())
				throw new DbStateException();
			for(int i = 0; i < batchAffected.length; i++) {
				if(batchAffected[i] != 1) throw new DbStateException();
			}
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			tryToClose(rs);
			throw new DbException(e);
		}
	}

	public void removeLocalAuthor(Connection txn, AuthorId a)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "DELETE FROM localAuthors WHERE authorId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, a.getBytes());
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void removeMessage(Connection txn, MessageId m) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "DELETE FROM messages WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

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
			if(affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
			return affected == 1;
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void removeOfferedMessages(Connection txn, ContactId c,
			Collection<MessageId> requested) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "DELETE FROM offers"
					+ " WHERE contactId = ? AND messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			for(MessageId m : requested) {
				ps.setBytes(2, m.getBytes());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if(batchAffected.length != requested.size())
				throw new DbStateException();
			for(int i = 0; i < batchAffected.length; i++) {
				if(batchAffected[i] != 1) throw new DbStateException();
			}
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void removeTransport(Connection txn, TransportId t)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "DELETE FROM transports WHERE transportId = ?";
			ps = txn.prepareStatement(sql);
			ps.setString(1, t.getString());
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void removeVisibility(Connection txn, ContactId c, GroupId g)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "DELETE FROM groupVisibilities"
					+ " WHERE contactId = ? AND groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setBytes(2, g.getBytes());
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			// Bump the subscription version
			sql = "UPDATE groupVersions"
					+ " SET localVersion = localVersion + 1, expiry = 0"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

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
			if(affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}
	public void setReorderingWindow(Connection txn, ContactId c, TransportId t,
			long period, long centre, byte[] bitmap) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE secrets SET centre = ?, bitmap = ?"
					+ " WHERE contactId = ? AND transportId = ? AND period = ?";
			ps = txn.prepareStatement(sql);
			ps.setLong(1, centre);
			ps.setBytes(2, bitmap);
			ps.setInt(3, c.getInt());
			ps.setString(4, t.getString());
			ps.setLong(5, period);
			int affected = ps.executeUpdate();
			if(affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public boolean setGroups(Connection txn, ContactId c,
			Collection<Group> groups, long version) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Mark the update as needing to be acked
			String sql = "UPDATE groupVersions"
					+ " SET remoteVersion = ?, remoteAcked = FALSE"
					+ " WHERE contactId = ? AND remoteVersion < ?";
			ps = txn.prepareStatement(sql);
			ps.setLong(1, version);
			ps.setInt(2, c.getInt());
			ps.setLong(3, version);
			int affected = ps.executeUpdate();
			if(affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
			// Return false if the update is obsolete
			if(affected == 0) return false;
			// Find any messages in groups that are being removed
			Set<GroupId> newIds = new HashSet<GroupId>();
			for(Group g : groups) newIds.add(g.getId());
			sql = "SELECT messageId, m.groupId"
					+ " FROM messages AS m"
					+ " JOIN contactGroups AS cg"
					+ " ON m.groupId = cg.groupId"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			List<MessageId> removed = new ArrayList<MessageId>();
			while(rs.next()) {
				if(!newIds.contains(new GroupId(rs.getBytes(2))))
					removed.add(new MessageId(rs.getBytes(1)));
			}
			rs.close();
			ps.close();
			// Reset any statuses for messages in groups that are being removed
			if(!removed.isEmpty()) {
				sql = "UPDATE statuses SET ack = FALSE, seen = FALSE,"
						+ " requested = FALSE, expiry = 0, txCount = 0"
						+ " WHERE contactId = ? AND messageId = ?";
				ps = txn.prepareStatement(sql);
				ps.setInt(1, c.getInt());
				for(MessageId m : removed) {
					ps.setBytes(2, m.getBytes());
					ps.addBatch();
				}
				int[] batchAffected = ps.executeBatch();
				if(batchAffected.length != removed.size())
					throw new DbStateException();
				for(int i = 0; i < batchAffected.length; i++) {
					if(batchAffected[i] < 0) throw new DbStateException();
				}
				ps.close();
			}
			// Delete the existing subscriptions, if any
			sql = "DELETE FROM contactGroups WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.executeUpdate();
			// Store the new subscriptions, if any
			if(groups.isEmpty()) return true;
			sql = "INSERT INTO contactGroups"
					+ " (contactId, groupId, name, salt)"
					+ " VALUES (?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			for(Group g : groups) {
				ps.setBytes(2, g.getId().getBytes());
				ps.setString(3, g.getName());
				ps.setBytes(4, g.getSalt());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if(batchAffected.length != groups.size())
				throw new DbStateException();
			for(int i = 0; i < batchAffected.length; i++) {
				if(batchAffected[i] != 1) throw new DbStateException();
			}
			ps.close();
			return true;
		} catch(SQLException e) {
			tryToClose(ps);
			tryToClose(rs);
			throw new DbException(e);
		}
	}

	public void setInboxGroup(Connection txn, ContactId c, Group g)
			throws DbException {
		PreparedStatement ps = null;
		try {
			// Unset any existing inbox group for the contact
			String sql = "UPDATE groupVisibilities"
					+ " SET inbox = FALSE"
					+ " WHERE contactId = ?"
					+ " AND inbox = TRUE";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.executeUpdate();
			int affected = ps.executeUpdate();
			if(affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
			// Make the group visible to the contact and set it as the inbox
			sql = "INSERT INTO groupVisibilities"
					+ " (contactId, groupId, inbox)"
					+ " VALUES (?, ?, TRUE)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setBytes(2, g.getId().getBytes());
			affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			// Add the group to the contact's subscriptions
			sql = "INSERT INTO contactGroups"
					+ " (contactId, groupId, name, salt)"
					+ " VALUES (?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setBytes(2, g.getId().getBytes());
			ps.setString(3, g.getName());
			ps.setBytes(4, g.getSalt());
			affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void setReadFlag(Connection txn, MessageId m, boolean read)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE messages SET read = ? WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBoolean(1, read);
			ps.setBytes(2, m.getBytes());
			int affected = ps.executeUpdate();
			if(affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void setRemoteProperties(Connection txn, ContactId c,
			Map<TransportId, TransportProperties> p) throws DbException {
		PreparedStatement ps = null;
		try {
			// Delete the existing properties, if any
			String sql = "DELETE FROM contactTransportProperties"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.executeUpdate();
			ps.close();
			// Store the new properties
			sql = "INSERT INTO contactTransportProperties"
					+ " (contactId, transportId, key, value)"
					+ " VALUES (?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			int batchSize = 0;
			for(Entry<TransportId, TransportProperties> e : p.entrySet()) {
				ps.setString(2, e.getKey().getString());
				for(Entry<String, String> e1 : e.getValue().entrySet()) {
					ps.setString(3, e1.getKey());
					ps.setString(4, e1.getValue());
					ps.addBatch();
					batchSize++;
				}
			}
			int[] batchAffected = ps.executeBatch();
			if(batchAffected.length != batchSize) throw new DbStateException();
			for(int i = 0; i < batchAffected.length; i++) {
				if(batchAffected[i] != 1) throw new DbStateException();
			}
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public boolean setRemoteProperties(Connection txn, ContactId c,
			TransportId t, TransportProperties p, long version)
					throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Find the existing version, if any
			String sql = "SELECT NULL FROM contactTransportVersions"
					+ " WHERE contactId = ? AND transportId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setString(2, t.getString());
			rs = ps.executeQuery();
			boolean found = rs.next();
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			// Mark the update as needing to be acked
			if(found) {
				// The row exists - update it
				sql = "UPDATE contactTransportVersions"
						+ " SET remoteVersion = ?, remoteAcked = FALSE"
						+ " WHERE contactId = ? AND transportId = ?"
						+ " AND remoteVersion < ?";
				ps = txn.prepareStatement(sql);
				ps.setLong(1, version);
				ps.setInt(2, c.getInt());
				ps.setString(3, t.getString());
				ps.setLong(4, version);
				int affected = ps.executeUpdate();
				if(affected < 0 || affected > 1) throw new DbStateException();
				ps.close();
				// Return false if the update is obsolete
				if(affected == 0) return false;
			} else {
				// The row doesn't exist - create it
				sql = "INSERT INTO contactTransportVersions (contactId,"
						+ " transportId, remoteVersion, remoteAcked)"
						+ " VALUES (?, ?, ?, FALSE)";
				ps = txn.prepareStatement(sql);
				ps.setInt(1, c.getInt());
				ps.setString(2, t.getString());
				ps.setLong(3, version);
				int affected = ps.executeUpdate();
				if(affected != 1) throw new DbStateException();
				ps.close();
			}
			// Delete the existing properties, if any
			sql = "DELETE FROM contactTransportProperties"
					+ " WHERE contactId = ? AND transportId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setString(2, t.getString());
			ps.executeUpdate();
			ps.close();
			// Store the new properties, if any
			if(p.isEmpty()) return true;
			sql = "INSERT INTO contactTransportProperties"
					+ " (contactId, transportId, key, value)"
					+ " VALUES (?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setString(2, t.getString());
			for(Entry<String, String> e : p.entrySet()) {
				ps.setString(3, e.getKey());
				ps.setString(4, e.getValue());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if(batchAffected.length != p.size()) throw new DbStateException();
			for(int i = 0; i < batchAffected.length; i++) {
				if(batchAffected[i] != 1) throw new DbStateException();
			}
			ps.close();
			return true;
		} catch(SQLException e) {
			tryToClose(ps);
			tryToClose(rs);
			throw new DbException(e);
		}
	}

	public boolean setRetentionTime(Connection txn, ContactId c, long retention,
			long version) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE retentionVersions SET retention = ?,"
					+ " remoteVersion = ?, remoteAcked = FALSE"
					+ " WHERE contactId = ? AND remoteVersion < ?";
			ps = txn.prepareStatement(sql);
			ps.setLong(1, retention);
			ps.setLong(2, version);
			ps.setInt(3, c.getInt());
			ps.setLong(4, version);
			int affected = ps.executeUpdate();
			if(affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
			return affected == 1;
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void setRetentionUpdateAcked(Connection txn, ContactId c,
			long version) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE retentionVersions SET localAcked = ?"
					+ " WHERE contactId = ?"
					+ " AND localAcked < ? AND localVersion >= ?";
			ps = txn.prepareStatement(sql);
			ps.setLong(1, version);
			ps.setInt(2, c.getInt());
			ps.setLong(3, version);
			ps.setLong(4, version);
			int affected = ps.executeUpdate();
			if(affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void setSubscriptionUpdateAcked(Connection txn, ContactId c,
			long version) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE groupVersions SET localAcked = ?"
					+ " WHERE contactId = ?"
					+ " AND localAcked < ? AND localVersion >= ?";
			ps = txn.prepareStatement(sql);
			ps.setLong(1, version);
			ps.setInt(2, c.getInt());
			ps.setLong(3, version);
			ps.setLong(4, version);
			int affected = ps.executeUpdate();
			if(affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void setTransportUpdateAcked(Connection txn, ContactId c,
			TransportId t, long version) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE transportVersions SET localAcked = ?"
					+ " WHERE contactId = ? AND transportId = ?"
					+ " AND localAcked < ? AND localVersion >= ?";
			ps = txn.prepareStatement(sql);
			ps.setLong(1, version);
			ps.setInt(2, c.getInt());
			ps.setString(3, t.getString());
			ps.setLong(4, version);
			ps.setLong(5, version);
			int affected = ps.executeUpdate();
			if(affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void setVisibleToAll(Connection txn, GroupId g, boolean all)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE groups SET visibleToAll = ? WHERE groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBoolean(1, all);
			ps.setBytes(2, g.getBytes());
			int affected = ps.executeUpdate();
			if(affected < 0 || affected > 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

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
			if(!rs.next()) throw new DbStateException();
			int txCount = rs.getInt(1);
			if(rs.next()) throw new DbStateException();
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
			if(affected != 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}
}
