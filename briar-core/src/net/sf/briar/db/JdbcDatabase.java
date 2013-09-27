package net.sf.briar.db;

import static java.sql.Types.BINARY;
import static java.sql.Types.VARCHAR;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.api.messaging.MessagingConstants.MAX_SUBSCRIPTIONS;
import static net.sf.briar.api.messaging.MessagingConstants.RETENTION_MODULUS;
import static net.sf.briar.db.ExponentialBackoff.calculateExpiry;

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
import java.util.logging.Logger;

import net.sf.briar.api.Author;
import net.sf.briar.api.AuthorId;
import net.sf.briar.api.Contact;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.LocalAuthor;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.clock.Clock;
import net.sf.briar.api.db.DbClosedException;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.GroupMessageHeader;
import net.sf.briar.api.db.PrivateMessageHeader;
import net.sf.briar.api.messaging.Group;
import net.sf.briar.api.messaging.GroupId;
import net.sf.briar.api.messaging.GroupStatus;
import net.sf.briar.api.messaging.Message;
import net.sf.briar.api.messaging.MessageId;
import net.sf.briar.api.messaging.RetentionAck;
import net.sf.briar.api.messaging.RetentionUpdate;
import net.sf.briar.api.messaging.SubscriptionAck;
import net.sf.briar.api.messaging.SubscriptionUpdate;
import net.sf.briar.api.messaging.TransportAck;
import net.sf.briar.api.messaging.TransportUpdate;
import net.sf.briar.api.transport.Endpoint;
import net.sf.briar.api.transport.TemporarySecret;

/**
 * A generic database implementation that can be used with any JDBC-compatible
 * database library.
 */
abstract class JdbcDatabase implements Database<Connection> {

	// Locking: identity
	private static final String CREATE_LOCAL_AUTHORS =
			"CREATE TABLE localAuthors"
					+ " (authorId HASH NOT NULL,"
					+ " name VARCHAR NOT NULL,"
					+ " publicKey BINARY NOT NULL,"
					+ " privateKey BINARY NOT NULL,"
					+ " PRIMARY KEY (authorId))";

	// Locking: contact
	// Dependents: message, retention, subscription, transport, window
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
					+ " ON DELETE RESTRICT)"; // Deletion not allowed

	private static final String INDEX_CONTACTS_BY_AUTHOR =
			"CREATE INDEX contactsByAuthor ON contacts (authorId)";

	// Locking: subscription
	// Dependents: message
	private static final String CREATE_GROUPS =
			"CREATE TABLE groups"
					+ " (groupId HASH NOT NULL,"
					+ " name VARCHAR NOT NULL,"
					+ " salt BINARY NOT NULL,"
					+ " visibleToAll BOOLEAN NOT NULL,"
					+ " PRIMARY KEY (groupId))";

	// Locking: subscription
	private static final String CREATE_GROUP_VISIBILITIES =
			"CREATE TABLE groupVisibilities"
					+ " (contactId INT NOT NULL,"
					+ " groupId HASH NOT NULL,"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE,"
					+ " FOREIGN KEY (groupId)"
					+ " REFERENCES groups (groupId)"
					+ " ON DELETE CASCADE)";

	// Locking: subscription
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

	// Locking: subscription
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

	// Locking: message
	private static final String CREATE_MESSAGES =
			"CREATE TABLE messages"
					+ " (messageId HASH NOT NULL,"
					+ " parentId HASH," // Null for the first msg in a thread
					+ " groupId HASH," // Null for private messages
					+ " authorId HASH," // Null for private/anon messages
					+ " authorName VARCHAR," // Null for private/anon messages
					+ " authorKey VARCHAR," // Null for private/anon messages
					+ " contentType VARCHAR NOT NULL,"
					+ " subject VARCHAR NOT NULL,"
					+ " timestamp BIGINT NOT NULL,"
					+ " length INT NOT NULL,"
					+ " bodyStart INT NOT NULL,"
					+ " bodyLength INT NOT NULL,"
					+ " raw BLOB NOT NULL,"
					+ " incoming BOOLEAN NOT NULL,"
					+ " contactId INT UNSIGNED," // Null for group messages
					+ " read BOOLEAN NOT NULL,"
					+ " starred BOOLEAN NOT NULL,"
					+ " PRIMARY KEY (messageId),"
					+ " FOREIGN KEY (groupId)"
					+ " REFERENCES groups (groupId)"
					+ " ON DELETE CASCADE,"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE)";

	private static final String INDEX_MESSAGES_BY_AUTHOR =
			"CREATE INDEX messagesByAuthor ON messages (authorId)";

	private static final String INDEX_MESSAGES_BY_TIMESTAMP =
			"CREATE INDEX messagesByTimestamp ON messages (timestamp)";

	// Locking: message
	private static final String CREATE_MESSAGES_TO_ACK =
			"CREATE TABLE messagesToAck"
					+ " (messageId HASH NOT NULL," // Not a foreign key
					+ " contactId INT NOT NULL,"
					+ " PRIMARY KEY (messageId, contactId),"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE)";

	// Locking: message
	private static final String CREATE_STATUSES =
			"CREATE TABLE statuses"
					+ " (messageId HASH NOT NULL,"
					+ " contactId INT NOT NULL,"
					+ " seen BOOLEAN NOT NULL,"
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

	// Locking: retention
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

	// Locking: transport
	// Dependents: window
	private static final String CREATE_TRANSPORTS =
			"CREATE TABLE transports"
					+ " (transportId HASH NOT NULL,"
					+ " maxLatency BIGINT NOT NULL,"
					+ " PRIMARY KEY (transportId))";

	// Locking: transport
	private static final String CREATE_TRANSPORT_CONFIGS =
			"CREATE TABLE transportConfigs"
					+ " (transportId HASH NOT NULL,"
					+ " key VARCHAR NOT NULL,"
					+ " value VARCHAR NOT NULL,"
					+ " PRIMARY KEY (transportId, key),"
					+ " FOREIGN KEY (transportId)"
					+ " REFERENCES transports (transportId)"
					+ " ON DELETE CASCADE)";

	// Locking: transport
	private static final String CREATE_TRANSPORT_PROPS =
			"CREATE TABLE transportProperties"
					+ " (transportId HASH NOT NULL,"
					+ " key VARCHAR NOT NULL,"
					+ " value VARCHAR NOT NULL,"
					+ " PRIMARY KEY (transportId, key),"
					+ " FOREIGN KEY (transportId)"
					+ " REFERENCES transports (transportId)"
					+ " ON DELETE CASCADE)";

	// Locking: transport
	private static final String CREATE_TRANSPORT_VERSIONS =
			"CREATE TABLE transportVersions"
					+ " (contactId INT NOT NULL,"
					+ " transportId HASH NOT NULL,"
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

	// Locking: transport
	private static final String CREATE_CONTACT_TRANSPORT_PROPS =
			"CREATE TABLE contactTransportProperties"
					+ " (contactId INT NOT NULL,"
					+ " transportId HASH NOT NULL," // Not a foreign key
					+ " key VARCHAR NOT NULL,"
					+ " value VARCHAR NOT NULL,"
					+ " PRIMARY KEY (contactId, transportId, key),"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE)";

	// Locking: transport
	private static final String CREATE_CONTACT_TRANSPORT_VERSIONS =
			"CREATE TABLE contactTransportVersions"
					+ " (contactId INT NOT NULL,"
					+ " transportId HASH NOT NULL," // Not a foreign key
					+ " remoteVersion BIGINT NOT NULL,"
					+ " remoteAcked BOOLEAN NOT NULL,"
					+ " PRIMARY KEY (contactId, transportId),"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE)";

	// Locking: window
	private static final String CREATE_ENDPOINTS =
			"CREATE TABLE endpoints"
					+ " (contactId INT NOT NULL,"
					+ " transportId HASH NOT NULL,"
					+ " epoch BIGINT NOT NULL,"
					+ " alice BOOLEAN NOT NULL,"
					+ " PRIMARY KEY (contactId, transportId),"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE,"
					+ " FOREIGN KEY (transportId)"
					+ " REFERENCES transports (transportId)"
					+ " ON DELETE CASCADE)";

	// Locking: window
	private static final String CREATE_SECRETS =
			"CREATE TABLE secrets"
					+ " (contactId INT NOT NULL,"
					+ " transportId HASH NOT NULL,"
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

	// Locking: window
	private static final String CREATE_CONNECTION_TIMES =
			"CREATE TABLE connectionTimes"
					+ " (contactId INT NOT NULL,"
					+ " lastConnected BIGINT NOT NULL,"
					+ " PRIMARY KEY (contactId),"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE)";

	private static final Logger LOG =
			Logger.getLogger(JdbcDatabase.class.getName());

	// Different database libraries use different names for certain types
	private final String hashType, binaryType, counterType, secretType;
	private final Clock clock;

	private final LinkedList<Connection> connections =
			new LinkedList<Connection>(); // Locking: self

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
			if(!reopen) createTables(txn);
			commitTransaction(txn);
		} catch(DbException e) {
			abortTransaction(txn);
			throw e;
		}
	}

	private void createTables(Connection txn) throws DbException {
		Statement s = null;
		try {
			s = txn.createStatement();
			s.executeUpdate(insertTypeNames(CREATE_LOCAL_AUTHORS));
			s.executeUpdate(insertTypeNames(CREATE_CONTACTS));
			s.executeUpdate(INDEX_CONTACTS_BY_AUTHOR);
			s.executeUpdate(insertTypeNames(CREATE_GROUPS));
			s.executeUpdate(insertTypeNames(CREATE_GROUP_VISIBILITIES));
			s.executeUpdate(insertTypeNames(CREATE_CONTACT_GROUPS));
			s.executeUpdate(insertTypeNames(CREATE_GROUP_VERSIONS));
			s.executeUpdate(insertTypeNames(CREATE_MESSAGES));
			s.executeUpdate(INDEX_MESSAGES_BY_AUTHOR);
			s.executeUpdate(INDEX_MESSAGES_BY_TIMESTAMP);
			s.executeUpdate(insertTypeNames(CREATE_MESSAGES_TO_ACK));
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
			s.executeUpdate(insertTypeNames(CREATE_CONNECTION_TIMES));
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

	private void tryToClose(Statement s) {
		if(s != null) try {
			s.close();
		} catch(SQLException e) {
			if(LOG.isLoggable(WARNING))LOG.log(WARNING, e.toString(), e);
		}
	}

	private void tryToClose(ResultSet rs) {
		if(rs != null) try {
			rs.close();
		} catch(SQLException e) {
			if(LOG.isLoggable(WARNING))LOG.log(WARNING, e.toString(), e);
		}
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
					if(LOG.isLoggable(INFO))
						LOG.info("Interrupted while closing connections");
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
			// Make groups that are visible to everyone visible to this contact
			sql = "SELECT groupId FROM groups WHERE visibleToAll = TRUE";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			Collection<byte[]> ids = new ArrayList<byte[]>();
			while(rs.next()) ids.add(rs.getBytes(1));
			rs.close();
			ps.close();
			if(!ids.isEmpty()) {
				sql = "INSERT INTO groupVisibilities (contactId, groupId)"
						+ " VALUES (?, ?)";
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
			// Create a connection time row
			sql = "INSERT INTO connectionTimes (contactId, lastConnected)"
					+ " VALUES (?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setLong(2, clock.currentTimeMillis());
			affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
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
			Collection<byte[]> transports = new ArrayList<byte[]>();
			while(rs.next()) transports.add(rs.getBytes(1));
			rs.close();
			ps.close();
			if(transports.isEmpty()) return c;
			sql = "INSERT INTO transportVersions (contactId, transportId,"
					+ " localVersion, localAcked, expiry, txCount)"
					+ " VALUES (?, ?, 1, 0, 0, 0)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			for(byte[] t : transports) {
				ps.setBytes(2, t);
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
			ps.setBytes(2, ep.getTransportId().getBytes());
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

	public boolean addGroupMessage(Connection txn, Message m, boolean incoming)
			throws DbException {
		if(m.getGroup() == null) throw new IllegalArgumentException();
		if(containsMessage(txn, m.getId())) return false;
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO messages (messageId, parentId, groupId,"
					+ " authorId, authorName, authorKey, contentType, subject,"
					+ " timestamp, length, bodyStart, bodyLength, raw,"
					+ " incoming, read, starred)"
					+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,"
					+ " FALSE, FALSE)";
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
			ps.setString(8, m.getSubject());
			ps.setLong(9, m.getTimestamp());
			byte[] raw = m.getSerialised();
			ps.setInt(10, raw.length);
			ps.setInt(11, m.getBodyStart());
			ps.setInt(12, m.getBodyLength());
			ps.setBytes(13, raw);
			ps.setBoolean(14, incoming);
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			return true;
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void addLocalAuthor(Connection txn, LocalAuthor a)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO localAuthors"
					+ " (authorId, name, publicKey, privateKey)"
					+ " VALUES (?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, a.getId().getBytes());
			ps.setString(2, a.getName());
			ps.setBytes(3, a.getPublicKey());
			ps.setBytes(4, a.getPrivateKey());
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void addMessageToAck(Connection txn, ContactId c, MessageId m)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT NULL FROM messagesToAck"
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
			sql = "INSERT INTO messagesToAck (messageId, contactId)"
					+ " VALUES (?, ?)";
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

	public boolean addPrivateMessage(Connection txn, Message m, ContactId c,
			boolean incoming) throws DbException {
		if(m.getGroup() != null) throw new IllegalArgumentException();
		if(m.getAuthor() != null) throw new IllegalArgumentException();
		if(containsMessage(txn, m.getId())) return false;
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO messages (messageId, parentId,"
					+ " contentType, subject, timestamp, length, bodyStart,"
					+ " bodyLength, raw, incoming, contactId, read, starred)"
					+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE, FALSE)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getId().getBytes());
			if(m.getParent() == null) ps.setNull(2, BINARY);
			else ps.setBytes(2, m.getParent().getBytes());
			ps.setString(3, m.getContentType());
			ps.setString(4, m.getSubject());
			ps.setLong(5, m.getTimestamp());
			byte[] raw = m.getSerialised();
			ps.setInt(6, raw.length);
			ps.setInt(7, m.getBodyStart());
			ps.setInt(8, m.getBodyLength());
			ps.setBytes(9, raw);
			ps.setBoolean(10, incoming);
			ps.setInt(11, c.getInt());
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			return true;
		} catch(SQLException e) {
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
				ps.setBytes(2, s.getTransportId().getBytes());
				ps.setLong(3, s.getPeriod());
				ps.setBytes(4, s.getSecret());
				ps.setLong(5, s.getOutgoingConnectionCounter());
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
				ps.setBytes(2, s.getTransportId().getBytes());
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

	public void addStatus(Connection txn, ContactId c, MessageId m,
			boolean seen) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO statuses"
					+ " (messageId, contactId, seen, expiry, txCount)"
					+ " VALUES (?, ?, ?, 0, 0)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setInt(2, c.getInt());
			ps.setBoolean(3, seen);
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public boolean addSubscription(Connection txn, Group g) throws DbException {
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
			sql = "INSERT INTO groups (groupId, name, salt, visibleToAll)"
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
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public boolean addTransport(Connection txn, TransportId t, long maxLatency)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Return false if the transport is already in the database
			String sql = "SELECT NULL FROM transports WHERE transportId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, t.getBytes());
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
			ps.setBytes(1, t.getBytes());
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
			ps.setBytes(2, t.getBytes());
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
			String sql = "INSERT INTO groupVisibilities (contactId, groupId)"
					+ " VALUES (?, ?)";
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

	public boolean containsSubscription(Connection txn, GroupId g)
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

	public boolean containsTransport(Connection txn, TransportId t)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT NULL FROM transports WHERE transportId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, t.getBytes());
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

	public boolean containsVisibleSubscription(Connection txn, ContactId c,
			GroupId g) throws DbException {
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

	public Collection<GroupStatus> getAvailableGroups(Connection txn)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Add all subscribed groups to the list
			String sql = "SELECT groupId, name, salt, visibleToAll FROM groups";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			List<GroupStatus> groups = new ArrayList<GroupStatus>();
			Set<GroupId> subscribed = new HashSet<GroupId>();
			while(rs.next()) {
				GroupId id = new GroupId(rs.getBytes(1));
				subscribed.add(id);
				String name = rs.getString(2);
				byte[] salt = rs.getBytes(3);
				Group group = new Group(id, name, salt);
				boolean visibleToAll = rs.getBoolean(4);
				groups.add(new GroupStatus(group, true, visibleToAll));
			}
			rs.close();
			ps.close();
			// Add all contact groups to the list, unless already added
			sql = "SELECT DISTINCT groupId, name, salt FROM contactGroups";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			while(rs.next()) {
				GroupId id = new GroupId(rs.getBytes(1));
				if(subscribed.contains(id)) continue;
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
			ps.setBytes(1, t.getBytes());
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
				TransportId transportId = new TransportId(rs.getBytes(2));
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

	public Collection<GroupMessageHeader> getGroupMessageHeaders(Connection txn,
			GroupId g) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT messageId, parentId, authorId, authorName,"
					+ " authorKey, contentType, subject, timestamp, read,"
					+ " starred"
					+ " FROM messages"
					+ " WHERE groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			rs = ps.executeQuery();
			List<GroupMessageHeader> headers =
					new ArrayList<GroupMessageHeader>();
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
				String subject = rs.getString(7);
				long timestamp = rs.getLong(8);
				boolean read = rs.getBoolean(9);
				boolean starred = rs.getBoolean(10);
				headers.add(new GroupMessageHeader(id, parent, author,
						contentType, subject, timestamp, read, starred, g));
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

	public MessageId getGroupMessageParent(Connection txn, MessageId m)
			throws DbException {
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

	public Collection<MessageId> getGroupMessages(Connection txn,
			AuthorId a) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT messageId"
					+ " FROM messages AS m"
					+ " JOIN groups AS g"
					+ " ON m.groupId = g.groupId"
					+ " WHERE authorId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, a.getBytes());
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

	public Map<ContactId, Long> getLastConnected(Connection txn)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT contactId, lastConnected FROM connectionTimes";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			Map<ContactId, Long> times = new HashMap<ContactId, Long>();
			while(rs.next())
				times.put(new ContactId(rs.getInt(1)), rs.getLong(2));
			rs.close();
			ps.close();
			return Collections.unmodifiableMap(times);
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
			String sql = "SELECT name, publicKey, privateKey FROM localAuthors"
					+ " WHERE authorId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, a.getBytes());
			rs = ps.executeQuery();
			if(!rs.next()) throw new DbStateException();
			LocalAuthor localAuthor = new LocalAuthor(a, rs.getString(1),
					rs.getBytes(2), rs.getBytes(3));
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
			String sql = "SELECT authorId, name, publicKey, privateKey"
					+ " FROM localAuthors";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			List<LocalAuthor> authors = new ArrayList<LocalAuthor>();
			while(rs.next()) {
				AuthorId authorId = new AuthorId(rs.getBytes(1));
				String name = rs.getString(2);
				byte[] publicKey = rs.getBytes(3);
				byte[] privateKey = rs.getBytes(4);
				authors.add(new LocalAuthor(authorId, name, publicKey,
						privateKey));
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
				TransportId id = new TransportId(rs.getBytes(1));
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
			ps.setBytes(1, t.getBytes());
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

	public Collection<MessageId> getMessagesToAck(Connection txn, ContactId c,
			int maxMessages) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT messageId FROM messagesToAck"
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

	public Collection<MessageId> getMessagesToOffer(Connection txn,
			ContactId c, int maxMessages) throws DbException {
		long now = clock.currentTimeMillis();
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Do we have any sendable private messages?
			String sql = "SELECT m.messageId FROM messages AS m"
					+ " JOIN statuses AS s"
					+ " ON m.messageId = s.messageId"
					+ " WHERE m.contactId = ? AND seen = FALSE AND expiry < ?"
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
			if(ids.size() == maxMessages)
				return Collections.unmodifiableList(ids);
			// Do we have any sendable group messages?
			sql = "SELECT m.messageId FROM messages AS m"
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
					+ " AND seen = FALSE AND s.expiry < ?"
					+ " ORDER BY timestamp DESC LIMIT ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setLong(2, now);
			ps.setInt(3, maxMessages - ids.size());
			rs = ps.executeQuery();
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

	public Collection<PrivateMessageHeader> getPrivateMessageHeaders(
			Connection txn, ContactId c) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Get the incoming message headers
			String sql = "SELECT m.messageId, parentId, contentType, subject,"
					+ " timestamp, read, starred, c.authorId, name, publicKey"
					+ " FROM messages AS m"
					+ " JOIN contacts AS c"
					+ " ON m.contactId = c.contactId"
					+ " WHERE m.contactId = ?"
					+ " AND groupId IS NULL"
					+ " AND incoming = TRUE";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			List<PrivateMessageHeader> headers =
					new ArrayList<PrivateMessageHeader>();
			while(rs.next()) {
				MessageId id = new MessageId(rs.getBytes(1));
				byte[] b = rs.getBytes(2);
				MessageId parent = b == null ? null : new MessageId(b);
				String contentType = rs.getString(3);
				String subject = rs.getString(4);
				long timestamp = rs.getLong(5);
				boolean read = rs.getBoolean(6);
				boolean starred = rs.getBoolean(7);
				AuthorId authorId = new AuthorId(rs.getBytes(8));
				String authorName = rs.getString(9);
				byte[] authorKey = rs.getBytes(10);
				Author author = new Author(authorId, authorName, authorKey);
				headers.add(new PrivateMessageHeader(id, parent, author,
						contentType, subject, timestamp, read, starred, c,
						true));
			}
			rs.close();
			ps.close();
			// Get the outgoing message headers
			sql = "SELECT m.messageId, parentId, contentType, subject,"
					+ " timestamp, read, starred, a.authorId, a.name,"
					+ " a.publicKey"
					+ " FROM messages AS m"
					+ " JOIN contacts AS c"
					+ " ON m.contactId = c.contactId"
					+ " JOIN localAuthors AS a"
					+ " ON c.localAuthorId = a.authorId"
					+ " WHERE m.contactId = ?"
					+ " AND groupId IS NULL"
					+ " AND incoming = FALSE";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			while(rs.next()) {
				MessageId id = new MessageId(rs.getBytes(1));
				byte[] b = rs.getBytes(2);
				MessageId parent = b == null ? null : new MessageId(b);
				String contentType = rs.getString(3);
				String subject = rs.getString(4);
				long timestamp = rs.getLong(5);
				boolean read = rs.getBoolean(6);
				boolean starred = rs.getBoolean(7);
				AuthorId authorId = new AuthorId(rs.getBytes(8));
				String authorName = rs.getString(9);
				byte[] authorKey = rs.getBytes(10);
				Author author = new Author(authorId, authorName, authorKey);
				headers.add(new PrivateMessageHeader(id, parent, author,
						contentType, subject, timestamp, read, starred, c,
						false));
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

	public byte[] getRawMessageIfSendable(Connection txn, ContactId c,
			MessageId m) throws DbException {
		long now = clock.currentTimeMillis();
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Do we have a sendable private message with the given ID?
			String sql = "SELECT length, raw FROM messages AS m"
					+ " JOIN statuses AS s"
					+ " ON m.messageId = s.messageId"
					+ " WHERE m.messageId = ? AND m.contactId = ?"
					+ " AND seen = FALSE AND expiry < ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setInt(2, c.getInt());
			ps.setLong(3, now);
			rs = ps.executeQuery();
			byte[] raw = null;
			if(rs.next()) {
				int length = rs.getInt(1);
				raw = rs.getBlob(2).getBytes(1, length);
				if(raw.length != length) throw new DbStateException();
			}
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			if(raw != null) return raw;
			// Do we have a sendable group message with the given ID?
			sql = "SELECT length, raw FROM messages AS m"
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
					+ " WHERE m.messageId = ?"
					+ " AND cg.contactId = ?"
					+ " AND timestamp >= retention"
					+ " AND seen = FALSE AND s.expiry < ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setInt(2, c.getInt());
			ps.setLong(3, now);
			rs = ps.executeQuery();
			if(rs.next()) {
				int length = rs.getInt(1);
				raw = rs.getBlob(2).getBytes(1, length);
				if(raw.length != length) throw new DbStateException();
			}
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
			boolean read = false;
			if(rs.next()) read = rs.getBoolean(1);
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
			ps.setBytes(1, t.getBytes());
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
			long maxLatency) throws DbException {
		long now = clock.currentTimeMillis();
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT timestamp, localVersion, txCount"
					+ " FROM messages AS m"
					+ " JOIN retentionVersions AS rv"
					+ " WHERE rv.contactId = ?"
					+ " AND localVersion > localAcked"
					+ " AND expiry < ?"
					+ " ORDER BY timestamp LIMIT 1";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setLong(2, now);
			rs = ps.executeQuery();
			if(!rs.next()) {
				rs.close();
				ps.close();
				return null;
			}
			long retention = rs.getLong(1);
			retention -= retention % RETENTION_MODULUS;
			long version = rs.getLong(2);
			int txCount = rs.getInt(3);
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
				TransportId transportId = new TransportId(rs.getBytes(2));
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

	public Collection<MessageId> getSendableMessages(Connection txn,
			ContactId c, int maxLength) throws DbException {
		long now = clock.currentTimeMillis();
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Do we have any sendable private messages?
			String sql = "SELECT length, m.messageId FROM messages AS m"
					+ " JOIN statuses AS s"
					+ " ON m.messageId = s.messageId"
					+ " WHERE m.contactId = ? AND seen = FALSE AND expiry < ?"
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
			if(total == maxLength) return Collections.unmodifiableList(ids);
			// Do we have any sendable group messages?
			sql = "SELECT length, m.messageId FROM messages AS m"
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
					+ " AND seen = FALSE AND s.expiry < ?"
					+ " ORDER BY timestamp DESC";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setLong(2, now);
			rs = ps.executeQuery();
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

	public boolean getStarredFlag(Connection txn, MessageId m)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT starred FROM messages WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			rs = ps.executeQuery();
			boolean starred = false;
			if(rs.next()) starred = rs.getBoolean(1);
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return starred;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public Collection<Group> getSubscriptions(Connection txn)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT groupId, name, salt FROM groups";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			List<Group> subs = new ArrayList<Group>();
			while(rs.next()) {
				GroupId groupId = new GroupId(rs.getBytes(1));
				String name = rs.getString(2);
				byte[] salt = rs.getBytes(3);
				subs.add(new Group(groupId, name, salt));
			}
			rs.close();
			ps.close();
			return Collections.unmodifiableList(subs);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public Collection<Group> getSubscriptions(Connection txn, ContactId c)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT groupId, name, salt FROM contactGroups"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			List<Group> subs = new ArrayList<Group>();
			while(rs.next()) {
				GroupId groupId = new GroupId(rs.getBytes(1));
				String name = rs.getString(2);
				byte[] salt = rs.getBytes(3);
				subs.add(new Group(groupId, name, salt));
			}
			rs.close();
			ps.close();
			return Collections.unmodifiableList(subs);
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
			long maxLatency) throws DbException {
		long now = clock.currentTimeMillis();
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT g.groupId, name, salt,"
					+ " localVersion, txCount"
					+ " FROM groups AS g"
					+ " JOIN groupVisibilities AS vis"
					+ " ON g.groupId = vis.groupId"
					+ " JOIN groupVersions AS ver"
					+ " ON vis.contactId = ver.contactId"
					+ " WHERE vis.contactId = ?"
					+ " AND localVersion > localAcked"
					+ " AND expiry < ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setLong(2, now);
			rs = ps.executeQuery();
			List<Group> subs = new ArrayList<Group>();
			long version = 0;
			int txCount = 0;
			while(rs.next()) {
				GroupId groupId = new GroupId(rs.getBytes(1));
				String name = rs.getString(2);
				byte[] salt = rs.getBytes(3);
				subs.add(new Group(groupId, name, salt));
				version = rs.getLong(4);
				txCount = rs.getInt(5);
			}
			rs.close();
			ps.close();
			if(subs.isEmpty()) return null;
			sql = "UPDATE groupVersions"
					+ " SET expiry = ?, txCount = txCount + 1"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setLong(1, calculateExpiry(now, maxLatency, txCount));
			ps.setInt(2, c.getInt());
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			subs = Collections.unmodifiableList(subs);
			return new SubscriptionUpdate(subs, version);
		} catch(SQLException e) {
			tryToClose(ps);
			tryToClose(rs);
			throw new DbException(e);
		}
	}

	public int getTransmissionCount(Connection txn, ContactId c, MessageId m)
			throws DbException {
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
			return txCount;
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
				TransportId id = new TransportId(rs.getBytes(1));
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
				ps.setBytes(2, a.getId().getBytes());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if(batchAffected.length != acks.size())
				throw new DbStateException();
			for(int i = 0; i < batchAffected.length; i++) {
				if(batchAffected[i] < 1) throw new DbStateException();
			}
			ps.close();
			return Collections.unmodifiableList(acks);
		} catch(SQLException e) {
			tryToClose(ps);
			tryToClose(rs);
			throw new DbException(e);
		}
	}

	public Map<TransportId, Long> getTransportLatencies(Connection txn)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT transportId, maxLatency FROM transports";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			Map<TransportId, Long> latencies = new HashMap<TransportId, Long>();
			while(rs.next()){
				TransportId id = new TransportId(rs.getBytes(1));
				latencies.put(id, rs.getLong(2));
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
			ContactId c, long maxLatency) throws DbException {
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
				TransportId id = new TransportId(rs.getBytes(1));
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
				ps.setBytes(3, u.getId().getBytes());
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
					+ " AND groupId IS NOT NULL"
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

	public Collection<GroupId> getVisibleSubscriptions(Connection txn,
			ContactId c) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT groupId FROM groupVisibilities"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			List<GroupId> visible = new ArrayList<GroupId>();
			while(rs.next()) visible.add(new GroupId(rs.getBytes(1)));
			rs.close();
			ps.close();
			return Collections.unmodifiableList(visible);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public boolean hasSendableMessages(Connection txn, ContactId c)
			throws DbException {
		long now = clock.currentTimeMillis();
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Do we have any sendable private messages?
			String sql = "SELECT m.messageId FROM messages AS m"
					+ " JOIN statuses AS s"
					+ " ON m.messageId = s.messageId"
					+ " WHERE m.contactId = ? AND seen = FALSE AND expiry < ?"
					+ " LIMIT 1";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setLong(2, now);
			rs = ps.executeQuery();
			boolean found = rs.next();
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			if(found) return true;
			// Do we have any sendable group messages?
			sql = "SELECT m.messageId FROM messages AS m"
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
					+ " AND seen = FALSE AND s.expiry < ?"
					+ " LIMIT 1";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setLong(2, now);
			rs = ps.executeQuery();
			found = rs.next();
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

	public long incrementConnectionCounter(Connection txn, ContactId c,
			TransportId t, long period) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Get the current connection counter
			String sql = "SELECT outgoing FROM secrets"
					+ " WHERE contactId = ? AND transportId = ? AND period = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setBytes(2, t.getBytes());
			ps.setLong(3, period);
			rs = ps.executeQuery();
			if(!rs.next()) {
				rs.close();
				ps.close();
				return -1;
			}
			long connection = rs.getLong(1);
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			// Increment the connection counter
			sql = "UPDATE secrets SET outgoing = outgoing + 1"
					+ " WHERE contactId = ? AND transportId = ? AND period = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setBytes(2, t.getBytes());
			ps.setLong(3, period);
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			return connection;
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

	public void removeOutstandingMessages(Connection txn, ContactId c,
			Collection<MessageId> acked) throws DbException {
		PreparedStatement ps = null;
		try {
			// Set the status of each message to seen = true
			String sql = "UPDATE statuses SET seen = TRUE"
					+ " WHERE messageId = ? AND contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			for(MessageId m : acked) {
				ps.setBytes(2, m.getBytes());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if(batchAffected.length != acked.size())
				throw new DbStateException();
			for(int i = 0; i < batchAffected.length; i++) {
				if(batchAffected[i] > 1) throw new DbStateException();
			}
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void removeMessagesToAck(Connection txn, ContactId c,
			Collection<MessageId> acked) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "DELETE FROM messagesToAck"
					+ " WHERE contactId = ? AND messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			for(MessageId m : acked) {
				ps.setBytes(2, m.getBytes());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if(batchAffected.length != acked.size())
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

	public void removeSubscription(Connection txn, GroupId g)
			throws DbException {
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
			// Bump the subscription version for the affected contacts
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

	public void removeTransport(Connection txn, TransportId t)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "DELETE FROM transports WHERE transportId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, t.getBytes());
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
			ps.setBytes(1, t.getBytes());
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
			ps.setBytes(2, t.getBytes());
			for(Entry<String, String> e : m.entrySet()) {
				ps.setString(1, e.getValue());
				ps.setString(3, e.getKey());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if(batchAffected.length != m.size()) throw new DbStateException();
			for(int i = 0; i < batchAffected.length; i++) {
				if(batchAffected[i] > 1) throw new DbStateException();
			}
			// Insert any properties that don't already exist
			sql = "INSERT INTO " + tableName + " (transportId, key, value)"
					+ " VALUES (?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, t.getBytes());
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

	public void setConnectionWindow(Connection txn, ContactId c, TransportId t,
			long period, long centre, byte[] bitmap) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE secrets SET centre = ?, bitmap = ?"
					+ " WHERE contactId = ? AND transportId = ? AND period = ?";
			ps = txn.prepareStatement(sql);
			ps.setLong(1, centre);
			ps.setBytes(2, bitmap);
			ps.setInt(3, c.getInt());
			ps.setBytes(4, t.getBytes());
			ps.setLong(5, period);
			int affected = ps.executeUpdate();
			if(affected > 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void setLastConnected(Connection txn, ContactId c, long now)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE connectionTimes SET lastConnected = ?"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setLong(1, now);
			ps.setInt(2, c.getInt());
			int affected = ps.executeUpdate();
			if(affected < 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public boolean setReadFlag(Connection txn, MessageId m, boolean read)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT read FROM messages WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			rs = ps.executeQuery();
			if(!rs.next()) throw new DbStateException();
			boolean wasRead = rs.getBoolean(1);
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			if(wasRead == read) return read;
			sql = "UPDATE messages SET read = ? WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBoolean(1, read);
			ps.setBytes(2, m.getBytes());
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			return !read;
		} catch(SQLException e) {
			tryToClose(rs);
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
				ps.setBytes(2, e.getKey().getBytes());
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
			ps.setBytes(2, t.getBytes());
			rs = ps.executeQuery();
			boolean exists = rs.next();
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			// Mark the update as needing to be acked
			if(exists) {
				// The row exists - update it
				sql = "UPDATE contactTransportVersions"
						+ " SET remoteVersion = ?, remoteAcked = FALSE"
						+ " WHERE contactId = ? AND transportId = ?"
						+ " AND remoteVersion < ?";
				ps = txn.prepareStatement(sql);
				ps.setLong(1, version);
				ps.setInt(2, c.getInt());
				ps.setBytes(3, t.getBytes());
				ps.setLong(4, version);
				int affected = ps.executeUpdate();
				if(affected > 1) throw new DbStateException();
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
				ps.setBytes(2, t.getBytes());
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
			ps.setBytes(2, t.getBytes());
			ps.executeUpdate();
			ps.close();
			// Store the new properties, if any
			if(p.isEmpty()) return true;
			sql = "INSERT INTO contactTransportProperties"
					+ " (contactId, transportId, key, value)"
					+ " VALUES (?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setBytes(2, t.getBytes());
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
			if(affected > 1) throw new DbStateException();
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
			if(affected > 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public boolean setStarredFlag(Connection txn, MessageId m, boolean starred)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT starred FROM messages WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			rs = ps.executeQuery();
			if(!rs.next()) throw new DbStateException();
			boolean wasStarred = rs.getBoolean(1);
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			if(wasStarred == starred) return starred;
			sql = "UPDATE messages SET starred = ? WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBoolean(1, starred);
			ps.setBytes(2, m.getBytes());
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			return !starred;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public boolean setStatusSeenIfVisible(Connection txn, ContactId c,
			MessageId m) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT NULL FROM messages AS m"
					+ " JOIN contactGroups AS cg"
					+ " ON m.groupId = cg.groupId"
					+ " JOIN groupVisibilities AS gv"
					+ " ON m.groupId = gv.groupId"
					+ " AND cg.contactId = gv.contactId"
					+ " JOIN retentionVersions AS rv"
					+ " ON cg.contactId = rv.contactId"
					+ " WHERE messageId = ?"
					+ " AND cg.contactId = ?"
					+ " AND timestamp >= retention";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setInt(2, c.getInt());
			rs = ps.executeQuery();
			boolean found = rs.next();
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			if(!found) return false;
			sql = "UPDATE statuses SET seen = ?"
					+ " WHERE messageId = ? AND contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBoolean(1, true);
			ps.setBytes(2, m.getBytes());
			ps.setInt(3, c.getInt());
			int affected = ps.executeUpdate();
			if(affected > 1) throw new DbStateException();
			ps.close();
			return true;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public boolean setSubscriptions(Connection txn, ContactId c,
			Collection<Group> subs, long version) throws DbException {
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
			if(affected > 1) throw new DbStateException();
			ps.close();
			// Return false if the update is obsolete
			if(affected == 0) return false;
			// Delete the existing subscriptions, if any
			sql = "DELETE FROM contactGroups WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.executeUpdate();
			// Store the new subscriptions, if any
			if(subs.isEmpty()) return true;
			sql = "INSERT INTO contactGroups"
					+ " (contactId, groupId, name, salt)"
					+ " VALUES (?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			for(Group g : subs) {
				ps.setBytes(2, g.getId().getBytes());
				ps.setString(3, g.getName());
				ps.setBytes(4, g.getSalt());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if(batchAffected.length != subs.size())
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
			if(affected > 1) throw new DbStateException();
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
			ps.setBytes(3, t.getBytes());
			ps.setLong(4, version);
			ps.setLong(5, version);
			int affected = ps.executeUpdate();
			if(affected > 1) throw new DbStateException();
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
			if(affected > 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void updateExpiryTimes(Connection txn, ContactId c,
			Map<MessageId, Integer> sent, long maxLatency) throws DbException {
		long now = clock.currentTimeMillis();
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE statuses"
					+ " SET expiry = ?, txCount = txCount + 1"
					+ " WHERE messageId = ? AND contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(3, c.getInt());
			for(Entry<MessageId, Integer> e : sent.entrySet()) {
				ps.setLong(1, calculateExpiry(now, maxLatency, e.getValue()));
				ps.setBytes(2, e.getKey().getBytes());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if(batchAffected.length != sent.size())
				throw new DbStateException();
			for(int i = 0; i < batchAffected.length; i++) {
				if(batchAffected[i] > 1) throw new DbStateException();
			}
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}
}
