package net.sf.briar.db;

import static java.sql.Types.BINARY;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.db.DatabaseConstants.EXPIRY_MODULUS;

import java.io.File;
import java.io.FileNotFoundException;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.Rating;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.db.DbClosedException;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.MessageHeader;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.ExpiryAck;
import net.sf.briar.api.protocol.ExpiryUpdate;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.SubscriptionAck;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.TransportAck;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.transport.ContactTransport;
import net.sf.briar.api.transport.TemporarySecret;
import net.sf.briar.util.FileUtils;

/**
 * A generic database implementation that can be used with any JDBC-compatible
 * database library. (Tested with H2, Derby and HSQLDB.)
 */
abstract class JdbcDatabase implements Database<Connection> {

	// Locking: contact
	private static final String CREATE_CONTACTS =
			"CREATE TABLE contacts"
					+ " (contactId COUNTER,"
					+ " PRIMARY KEY (contactId))";

	// Locking: expiry
	private static final String CREATE_EXPIRY_VERSIONS =
			"CREATE TABLE expiryVersions"
					+ " (contactId INT NOT NULL,"
					+ " expiry BIGINT NOT NULL,"
					+ " localVersion BIGINT NOT NULL,"
					+ " localAcked BIGINT NOT NULL,"
					+ " remoteVersion BIGINT NOT NULL,"
					+ " remoteAcked BOOLEAN NOT NULL,"
					+ " PRIMARY KEY (contactId),"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE)";

	// Locking: message
	private static final String CREATE_MESSAGES =
			"CREATE TABLE messages"
					+ " (messageId HASH NOT NULL,"
					+ " parentId HASH," // Null for the first msg in a thread
					+ " groupId HASH," // Null for private messages
					+ " authorId HASH," // Null for private or anonymous msgs
					+ " subject VARCHAR NOT NULL,"
					+ " timestamp BIGINT NOT NULL,"
					+ " length INT NOT NULL,"
					+ " bodyStart INT NOT NULL,"
					+ " bodyLength INT NOT NULL,"
					+ " raw BLOB NOT NULL,"
					+ " sendability INT," // Null for private messages
					+ " contactId INT," // Null for group messages
					+ " PRIMARY KEY (messageId),"
					+ " FOREIGN KEY (groupId)"
					+ " REFERENCES groups (groupId)"
					+ " ON DELETE CASCADE,"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE)";

	private static final String INDEX_MESSAGES_BY_PARENT =
			"CREATE INDEX messagesByParent ON messages (parentId)";

	private static final String INDEX_MESSAGES_BY_AUTHOR =
			"CREATE INDEX messagesByAuthor ON messages (authorId)";

	private static final String INDEX_MESSAGES_BY_TIMESTAMP =
			"CREATE INDEX messagesByTimestamp ON messages (timestamp)";

	private static final String INDEX_MESSAGES_BY_SENDABILITY =
			"CREATE INDEX messagesBySendability ON messages (sendability)";

	// Locking: contact read, message
	private static final String CREATE_MESSAGES_TO_ACK =
			"CREATE TABLE messagesToAck"
					+ " (messageId HASH NOT NULL,"
					+ " contactId INT NOT NULL,"
					+ " PRIMARY KEY (messageId, contactId),"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE)";

	// Locking: contact read, message
	private static final String CREATE_STATUSES =
			"CREATE TABLE statuses"
					+ " (messageId HASH NOT NULL,"
					+ " contactId INT NOT NULL,"
					+ " status SMALLINT NOT NULL,"
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

	// Locking: message
	private static final String CREATE_FLAGS =
			"CREATE TABLE flags"
					+ " (messageId HASH NOT NULL,"
					+ " read BOOLEAN NOT NULL,"
					+ " starred BOOLEAN NOT NULL,"
					+ " PRIMARY KEY (messageId),"
					+ " FOREIGN KEY (messageId)"
					+ " REFERENCES messages (messageId)"
					+ " ON DELETE CASCADE)";

	// Locking: rating
	private static final String CREATE_RATINGS =
			"CREATE TABLE ratings"
					+ " (authorId HASH NOT NULL,"
					+ " rating SMALLINT NOT NULL,"
					+ " PRIMARY KEY (authorId))";

	// Locking: subscription
	private static final String CREATE_GROUPS =
			"CREATE TABLE groups"
					+ " (groupId HASH NOT NULL,"
					+ " name VARCHAR NOT NULL,"
					+ " key BINARY," // Null for unrestricted groups
					+ " PRIMARY KEY (groupId))";

	// Locking: contact read, subscription
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

	// Locking: contact read, subscription
	private static final String CREATE_CONTACT_GROUPS =
			"CREATE TABLE contactGroups"
					+ " (contactId INT NOT NULL,"
					+ " groupId HASH NOT NULL," // Not a foreign key
					+ " name VARCHAR NOT NULL,"
					+ " key BINARY," // Null for unrestricted groups
					+ " PRIMARY KEY (contactId, groupId),"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE)";

	// Locking: contact read, subscription
	private static final String CREATE_GROUP_VERSIONS =
			"CREATE TABLE groupVersions"
					+ " (contactId INT NOT NULL,"
					+ " localVersion BIGINT NOT NULL,"
					+ " localAcked BIGINT NOT NULL,"
					+ " remoteVersion BIGINT NOT NULL,"
					+ " remoteAcked BOOLEAN NOT NULL,"
					+ " PRIMARY KEY (contactId),"
					+ " FOREIGN KEY (contactid)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE)";

	// Locking: transport
	private static final String CREATE_TRANSPORTS =
			"CREATE TABLE transports"
					+ " (transportId HASH NOT NULL,"
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

	// Locking: contact read, transport
	private static final String CREATE_TRANSPORT_VERSIONS =
			"CREATE TABLE transportVersions"
					+ " (contactId HASH NOT NULL,"
					+ " transportId HASH NOT NULL,"
					+ " localVersion BIGINT NOT NULL,"
					+ " localAcked BIGINT NOT NULL,"
					+ " PRIMARY KEY (contactId, transportId),"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE,"
					+ " FOREIGN KEY (transportId)"
					+ " REFERENCES transports (transportId)"
					+ " ON DELETE CASCADE)";

	// Locking: contact read, transport
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

	// Locking: contact read, transport
	private static final String CREATE_CONTACT_TRANSPORT_VERSIONS =
			"CREATE TABLE contactTransportVersions"
					+ " (contactId HASH NOT NULL,"
					+ " transportId HASH NOT NULL," // Not a foreign key
					+ " remoteVersion BIGINT NOT NULL,"
					+ " remoteAcked BOOLEAN NOT NULL,"
					+ " PRIMARY KEY (contactId, transportId),"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE)";

	// Locking: contact read, transport read, window
	private static final String CREATE_CONTACT_TRANSPORTS =
			"CREATE TABLE contactTransports"
					+ " (contactId INT NOT NULL,"
					+ " transportId HASH NOT NULL,"
					+ " epoch BIGINT NOT NULL,"
					+ " clockDiff BIGINT NOT NULL,"
					+ " latency BIGINT NOT NULL,"
					+ " alice BOOLEAN NOT NULL,"
					+ " PRIMARY KEY (contactId, transportId),"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE,"
					+ " FOREIGN KEY (transportId)"
					+ " REFERENCES transports (transportId)"
					+ " ON DELETE CASCADE)";

	// Locking: contact read, transport read, window
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

	private static final Logger LOG =
			Logger.getLogger(JdbcDatabase.class.getName());

	// Different database libraries use different names for certain types
	private final String hashType, binaryType, counterType, secretType;

	private final LinkedList<Connection> connections =
			new LinkedList<Connection>(); // Locking: self

	private int openConnections = 0; // Locking: connections
	private boolean closed = false; // Locking: connections

	protected abstract Connection createConnection() throws SQLException;

	JdbcDatabase(String hashType, String binaryType, String counterType,
			String secretType) {
		this.hashType = hashType;
		this.binaryType = binaryType;
		this.counterType = counterType;
		this.secretType = secretType;
	}

	protected void open(boolean resume, File dir, String driverClass)
			throws DbException, IOException {
		if(resume) {
			if(!dir.exists()) throw new FileNotFoundException();
			if(!dir.isDirectory()) throw new FileNotFoundException();
		} else {
			if(dir.exists()) FileUtils.delete(dir);
		}
		// Load the JDBC driver
		try {
			Class.forName(driverClass);
		} catch(ClassNotFoundException e) {
			throw new DbException(e);
		}
		// Open the database
		Connection txn = startTransaction();
		try {
			// If not resuming, create the tables
			if(!resume) createTables(txn);
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
			s.executeUpdate(insertTypeNames(CREATE_CONTACTS));
			s.executeUpdate(insertTypeNames(CREATE_EXPIRY_VERSIONS));
			s.executeUpdate(insertTypeNames(CREATE_MESSAGES));
			s.executeUpdate(INDEX_MESSAGES_BY_PARENT);
			s.executeUpdate(INDEX_MESSAGES_BY_AUTHOR);
			s.executeUpdate(INDEX_MESSAGES_BY_TIMESTAMP);
			s.executeUpdate(INDEX_MESSAGES_BY_SENDABILITY);
			s.executeUpdate(insertTypeNames(CREATE_MESSAGES_TO_ACK));
			s.executeUpdate(insertTypeNames(CREATE_STATUSES));
			s.executeUpdate(INDEX_STATUSES_BY_MESSAGE);
			s.executeUpdate(INDEX_STATUSES_BY_CONTACT);
			s.executeUpdate(insertTypeNames(CREATE_FLAGS));
			s.executeUpdate(insertTypeNames(CREATE_RATINGS));
			s.executeUpdate(insertTypeNames(CREATE_GROUPS));
			s.executeUpdate(insertTypeNames(CREATE_GROUP_VISIBILITIES));
			s.executeUpdate(insertTypeNames(CREATE_CONTACT_GROUPS));
			s.executeUpdate(insertTypeNames(CREATE_GROUP_VERSIONS));
			s.executeUpdate(insertTypeNames(CREATE_TRANSPORTS));
			s.executeUpdate(insertTypeNames(CREATE_TRANSPORT_CONFIGS));
			s.executeUpdate(insertTypeNames(CREATE_TRANSPORT_PROPS));
			s.executeUpdate(insertTypeNames(CREATE_TRANSPORT_VERSIONS));
			s.executeUpdate(insertTypeNames(CREATE_CONTACT_TRANSPORT_PROPS));
			s.executeUpdate(insertTypeNames(CREATE_CONTACT_TRANSPORT_VERSIONS));
			s.executeUpdate(insertTypeNames(CREATE_CONTACT_TRANSPORTS));
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
				synchronized(connections) {
					openConnections++;
				}
			}
			txn.setAutoCommit(false);
		} catch(SQLException e) {
			throw new DbException(e);
		}
		return txn;
	}

	public void abortTransaction(Connection txn) {
		try {
			txn.rollback();
			txn.setAutoCommit(true);
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
		try {
			txn.commit();
			txn.setAutoCommit(true);
		} catch(SQLException e) {
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

	public ContactId addContact(Connection txn) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Create a contact row
			String sql = "INSERT INTO contacts DEFAULT VALUES";
			ps = txn.prepareStatement(sql);
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			// Get the new (highest) contact ID
			sql = "SELECT contactId FROM contacts"
					+ " ORDER BY contactId DESC LIMIT ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, 1);
			rs = ps.executeQuery();
			if(!rs.next()) throw new DbStateException();
			ContactId c = new ContactId(rs.getInt(1));
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			// Create an expiry version row
			sql = "INSERT INTO expiryVersions (contactId, expiry,"
					+ " localVersion, localAcked, remoteVersion, remoteAcked)"
					+ " VALUES (?, ZERO(), ?, ZERO(), ZERO(), TRUE)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setInt(2, 1);
			affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			// Create a group version row
			sql = "INSERT INTO groupVersions (contactId, localVersion,"
					+ " localAcked, remoteVersion, remoteAcked)"
					+ " VALUES (?, ?, ZERO(), ZERO(), TRUE)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setInt(2, 1);
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
			sql = "INSERT INTO transportVersions"
					+ " (contactId, transportId, localVersion, localAcked)"
					+ " VALUES (?, ?, ?, ZERO())";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setInt(3, 1);
			for(byte[] t : transports) {
				ps.setBytes(2, t);
				ps.addBatch();
			}
			int[] affectedBatch = ps.executeBatch();
			if(affectedBatch.length != transports.size())
				throw new DbStateException();
			for(int i = 0; i < affectedBatch.length; i++) {
				if(affectedBatch[i] != 1) throw new DbStateException();
			}
			ps.close();
			return c;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void addContactTransport(Connection txn, ContactTransport ct)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO contactTransports (contactId,"
					+ " transportId, epoch, clockDiff, latency, alice)"
					+ " VALUES (?, ?, ?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, ct.getContactId().getInt());
			ps.setBytes(2, ct.getTransportId().getBytes());
			ps.setLong(3, ct.getEpoch());
			ps.setLong(4, ct.getClockDifference());
			ps.setLong(5, ct.getLatency());
			ps.setBoolean(6, ct.getAlice());
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public boolean addGroupMessage(Connection txn, Message m)
			throws DbException {
		assert m.getGroup() != null;
		if(containsMessage(txn, m.getId())) return false;
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO messages (messageId, parentId, groupId,"
					+ " authorId, subject, timestamp, length, bodyStart,"
					+ " bodyLength, raw, sendability)"
					+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ZERO())";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getId().getBytes());
			if(m.getParent() == null) ps.setNull(2, BINARY);
			else ps.setBytes(2, m.getParent().getBytes());
			ps.setBytes(3, m.getGroup().getBytes());
			if(m.getAuthor() == null) ps.setNull(4, BINARY);
			else ps.setBytes(4, m.getAuthor().getBytes());
			ps.setString(5, m.getSubject());
			ps.setLong(6, m.getTimestamp());
			byte[] raw = m.getSerialised();
			ps.setInt(7, raw.length);
			ps.setInt(8, m.getBodyStart());
			ps.setInt(9, m.getBodyLength());
			ps.setBytes(10, raw);
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			return true;
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

	public void addOutstandingMessages(Connection txn, ContactId c,
			Collection<MessageId> sent) throws DbException {
		PreparedStatement ps = null;
		try {
			// Set the status of each message to SENT if it's currently NEW
			String sql = "UPDATE statuses SET status = ?"
					+ " WHERE messageId = ? AND contactId = ? AND status = ?";
			ps = txn.prepareStatement(sql);
			ps.setShort(1, (short) Status.SENT.ordinal());
			ps.setInt(3, c.getInt());
			ps.setShort(4, (short) Status.NEW.ordinal());
			for(MessageId m : sent) {
				ps.setBytes(2, m.getBytes());
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

	public boolean addPrivateMessage(Connection txn, Message m, ContactId c)
			throws DbException {
		assert m.getGroup() == null;
		if(containsMessage(txn, m.getId())) return false;
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO messages"
					+ " (messageId, parentId, subject, timestamp, length,"
					+ " bodyStart, bodyLength, raw, contactId)"
					+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getId().getBytes());
			if(m.getParent() == null) ps.setNull(2, BINARY);
			else ps.setBytes(2, m.getParent().getBytes());
			ps.setString(3, m.getSubject());
			ps.setLong(4, m.getTimestamp());
			byte[] raw = m.getSerialised();
			ps.setInt(5, raw.length);
			ps.setInt(6, m.getBodyStart());
			ps.setInt(7, m.getBodyLength());
			ps.setBytes(8, raw);
			ps.setInt(9, c.getInt());
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			return true;
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void addSubscription(Connection txn, Group g) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO groups"
					+ " (groupId, name, key) VALUES (?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getId().getBytes());
			ps.setString(2, g.getName());
			ps.setBytes(3, g.getPublicKey());
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
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
				ps.setLong(3, s.getPeriod() - 1);
				ps.addBatch();
			}
			batchAffected = ps.executeBatch();
			if(batchAffected.length != secrets.size())
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

	public void addTransport(Connection txn, TransportId t) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Create a transport row
			String sql = "INSERT INTO transports (transportId) VALUES (?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, t.getBytes());
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
			if(contacts.isEmpty()) return;
			sql = "INSERT INTO transportVersions"
					+ " (contactId, transportId, localVersion, localAcked)"
					+ " VALUES (?, ?, ?, ZERO())";
			ps = txn.prepareStatement(sql);
			ps.setBytes(2, t.getBytes());
			ps.setInt(3, 1);
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
			ps.setBytes(3, g.getBytes());
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			// Bump the subscription version
			sql = "UPDATE groupVersions SET localVersion = localVersion + ?"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, 1);
			ps.setInt(2, c.getInt());
			affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
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

	public boolean containsContactTransport(Connection txn, ContactId c,
			TransportId t) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT NULL FROM contactTransports"
					+ " WHERE contactId = ? AND transportId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setBytes(2, t.getBytes());
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

	public boolean containsVisibleSubscription(Connection txn, ContactId c,
			GroupId g) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT NULL FROM groups AS g"
					+ " JOIN groupVisibilities AS gv"
					+ " ON g.groupId = gv.groupId"
					+ " WHERE g.groupId = ? AND contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
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

	public Collection<ContactId> getContacts(Connection txn)
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

	public Collection<ContactTransport> getContactTransports(Connection txn)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT contactId, transportId, epoch, clockDiff,"
					+ " latency, alice"
					+ " FROM contactTransports";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			List<ContactTransport> cts = new ArrayList<ContactTransport>();
			while(rs.next()) {
				ContactId c = new ContactId(rs.getInt(1));
				TransportId t = new TransportId(rs.getBytes(2));
				long epoch = rs.getLong(3);
				long clockDiff = rs.getLong(4);
				long latency = rs.getLong(5);
				boolean alice = rs.getBoolean(6);
				cts.add(new ContactTransport(c, t, epoch, clockDiff, latency,
						alice));
			}
			return Collections.unmodifiableList(cts);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	protected long getDiskSpace(File f) {
		long total = 0L;
		if(f.isDirectory()) {
			for(File child : f.listFiles()) total += getDiskSpace(child);
			return total;
		} else return f.length();
	}

	public ExpiryAck getExpiryAck(Connection txn, ContactId c)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT remoteVersion FROM expiryVersions"
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
			sql = "UPDATE expiryVersions SET remoteAcked = TRUE"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			return new ExpiryAck(version);
		} catch(SQLException e) {
			tryToClose(ps);
			tryToClose(rs);
			throw new DbException(e);
		}
	}

	public ExpiryUpdate getExpiryUpdate(Connection txn, ContactId c)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT timestamp, localVersion"
					+ " FROM messages JOIN expiryVersions"
					+ " WHERE contactId = ? AND localVersion > localAcked"
					+ " ORDER BY timestamp LIMIT ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setInt(2, 1);
			rs = ps.executeQuery();
			if(!rs.next()) {
				rs.close();
				ps.close();
				return null;
			}
			long expiry = rs.getLong(1);
			expiry -= expiry % EXPIRY_MODULUS;
			long version = rs.getLong(2);
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return new ExpiryUpdate(expiry, version);
		} catch(SQLException e) {
			tryToClose(ps);
			tryToClose(rs);
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

	public byte[] getMessage(Connection txn, MessageId m) throws DbException {
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
			String sql = "SELECT m.messageId, parentId, authorId,"
					+ " subject, timestamp, read, starred"
					+ " FROM messages AS m"
					+ " LEFT OUTER JOIN flags AS f"
					+ " ON m.messageId = f.messageId"
					+ " WHERE groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			rs = ps.executeQuery();
			List<MessageHeader> headers = new ArrayList<MessageHeader>();
			while(rs.next()) {
				MessageId id = new MessageId(rs.getBytes(1));
				byte[] p = rs.getBytes(2);
				MessageId parent = p == null ? null : new MessageId(p);
				AuthorId author = new AuthorId(rs.getBytes(3));
				String subject = rs.getString(4);
				long timestamp = rs.getLong(5);
				boolean read = rs.getBoolean(6); // False if absent
				boolean starred = rs.getBoolean(7); // False if absent
				headers.add(new MessageHeaderImpl(id, parent, g, author,
						subject, timestamp, read, starred));
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

	public byte[] getMessageIfSendable(Connection txn, ContactId c, MessageId m)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Do we have a sendable private message with the given ID?
			String sql = "SELECT length, raw FROM messages AS m"
					+ " JOIN statuses AS s"
					+ " ON m.messageId = s.messageId"
					+ " WHERE m.messageId = ? AND m.contactId = ?"
					+ " AND status = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setInt(2, c.getInt());
			ps.setShort(3, (short) Status.NEW.ordinal());
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
					+ " JOIN expiryVersions AS ev"
					+ " ON cg.contactId = ev.contactId"
					+ " JOIN statuses AS s"
					+ " ON m.messageId = s.messageId"
					+ " AND cg.contactId = s.contactId"
					+ " WHERE m.messageId = ?"
					+ " AND cg.contactId = ?"
					+ " AND timestamp >= expiry"
					+ " AND status = ?"
					+ " AND sendability > ZERO()";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setInt(2, c.getInt());
			ps.setShort(3, (short) Status.NEW.ordinal());
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

	public Collection<MessageId> getMessagesByAuthor(Connection txn, AuthorId a)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT messageId FROM messages WHERE authorId = ?";
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
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Do we have any sendable private messages?
			String sql = "SELECT m.messageId FROM messages AS m"
					+ " JOIN statuses AS s"
					+ " ON m.messageId = s.messageId"
					+ " WHERE m.contactId = ? AND status = ?"
					+ " ORDER BY timestamp"
					+ " LIMIT ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setShort(2, (short) Status.NEW.ordinal());
			ps.setInt(3, maxMessages);
			rs = ps.executeQuery();
			List<MessageId> ids = new ArrayList<MessageId>();
			while(rs.next()) ids.add(new MessageId(rs.getBytes(2)));
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
					+ " JOIN expiryVersions AS ev"
					+ " ON cg.contactId = ev.contactId"
					+ " JOIN statuses AS s"
					+ " ON m.messageId = s.messageId"
					+ " AND cg.contactId = s.contactId"
					+ " WHERE cg.contactId = ?"
					+ " AND timestamp >= expiry"
					+ " AND status = ?"
					+ " AND sendability > ZERO()"
					+ " ORDER BY timestamp"
					+ " LIMIT ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setShort(2, (short) Status.NEW.ordinal());
			ps.setInt(3, maxMessages - ids.size());
			rs = ps.executeQuery();
			while(rs.next()) ids.add(new MessageId(rs.getBytes(2)));
			rs.close();
			ps.close();
			return Collections.unmodifiableList(ids);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public int getNumberOfSendableChildren(Connection txn, MessageId m)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Children in other groups should not be counted
			String sql = "SELECT groupId FROM messages WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			rs = ps.executeQuery();
			if(!rs.next()) throw new DbStateException();
			byte[] groupId = rs.getBytes(1);
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			sql = "SELECT COUNT (messageId) FROM messages"
					+ " WHERE parentId = ? AND groupId = ?"
					+ " AND sendability > ZERO()";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setBytes(2, groupId);
			rs = ps.executeQuery();
			if(!rs.next()) throw new DbStateException();
			int count = rs.getInt(1);
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return count;
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

	public Rating getRating(Connection txn, AuthorId a) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT rating FROM ratings WHERE authorId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, a.getBytes());
			rs = ps.executeQuery();
			Rating r;
			if(rs.next()) r = Rating.values()[rs.getByte(1)];
			else r = Rating.UNRATED;
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return r;
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
			String sql = "SELECT read FROM flags WHERE messageId = ?";
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

	public Collection<TemporarySecret> getSecrets(Connection txn)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT ct.contactId, ct.transportId, epoch,"
					+ " clockDiff, latency, alice, period, secret, outgoing,"
					+ " centre, bitmap"
					+ " FROM contactTransports AS ct"
					+ " JOIN secrets AS s"
					+ " ON ct.contactId = s.contactId"
					+ " AND ct.transportId = s.transportId";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			List<TemporarySecret> secrets = new ArrayList<TemporarySecret>();
			while(rs.next()) {
				ContactId c = new ContactId(rs.getInt(1));
				TransportId t = new TransportId(rs.getBytes(2));
				long epoch = rs.getLong(3);
				long clockDiff = rs.getLong(4);
				long latency = rs.getLong(5);
				boolean alice = rs.getBoolean(6);
				long period = rs.getLong(7);
				byte[] secret = rs.getBytes(8);
				long outgoing = rs.getLong(9);
				long centre = rs.getLong(10);
				byte[] bitmap = rs.getBytes(11);
				secrets.add(new TemporarySecret(c, t, epoch, clockDiff, latency,
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

	public int getSendability(Connection txn, MessageId m) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT sendability FROM messages WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			rs = ps.executeQuery();
			if(!rs.next()) throw new DbStateException();
			int sendability = rs.getInt(1);
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return sendability;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public Collection<MessageId> getSendableMessages(Connection txn,
			ContactId c, int maxLength) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Do we have any sendable private messages?
			String sql = "SELECT length, m.messageId FROM messages AS m"
					+ " JOIN statuses AS s"
					+ " ON m.messageId = s.messageId"
					+ " WHERE m.contactId = ? AND status = ?"
					+ " ORDER BY timestamp";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setShort(2, (short) Status.NEW.ordinal());
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
					+ " JOIN expiryVersions AS ev"
					+ " ON cg.contactId = ev.contactId"
					+ " JOIN statuses AS s"
					+ " ON m.messageId = s.messageId"
					+ " AND cg.contactId = s.contactId"
					+ " WHERE cg.contactId = ?"
					+ " AND timestamp >= expiry"
					+ " AND status = ?"
					+ " AND sendability > ZERO()"
					+ " ORDER BY timestamp";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setShort(2, (short) Status.NEW.ordinal());
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

	public boolean getStarredFlag(Connection txn, MessageId m) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT starred FROM flags WHERE messageId = ?";
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
			String sql = "SELECT groupId, name, key FROM groups";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			List<Group> subs = new ArrayList<Group>();
			while(rs.next()) {
				GroupId id = new GroupId(rs.getBytes(1));
				String name = rs.getString(2);
				byte[] publicKey = rs.getBytes(3);
				subs.add(new Group(id, name, publicKey));
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
			String sql = "SELECT groupId, name, key FROM contactGroups"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			List<Group> subs = new ArrayList<Group>();
			while(rs.next()) {
				GroupId id = new GroupId(rs.getBytes(1));
				String name = rs.getString(2);
				byte[] publicKey = rs.getBytes(3);
				subs.add(new Group(id, name, publicKey));
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

	public SubscriptionUpdate getSubscriptionUpdate(Connection txn, ContactId c)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT g.groupId, name, key, localVersion"
					+ " FROM groups AS g"
					+ " JOIN groupVisibilities AS gv"
					+ " ON g.groupId = gv.groupId"
					+ " JOIN groupVersions AS v"
					+ " ON gv.contactId = v.contactId"
					+ " WHERE gv.contactId = ?"
					+ " AND localVersion > localAcked";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			List<Group> subs = new ArrayList<Group>();
			long version = 0L;
			while(rs.next()) {
				byte[] id = rs.getBytes(1);
				String name = rs.getString(2);
				byte[] key = rs.getBytes(3);
				version = rs.getLong(4);
				subs.add(new Group(new GroupId(id), name, key));
			}
			rs.close();
			ps.close();
			if(subs.isEmpty()) return null;
			subs = Collections.unmodifiableList(subs);
			return new SubscriptionUpdate(subs, version);
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
			int[] affectedBatch = ps.executeBatch();
			if(affectedBatch.length != acks.size())
				throw new DbStateException();
			for(int i = 0; i < affectedBatch.length; i++) {
				if(affectedBatch[i] < 1) throw new DbStateException();
			}
			ps.close();
			return Collections.unmodifiableList(acks);
		} catch(SQLException e) {
			tryToClose(ps);
			tryToClose(rs);
			throw new DbException(e);
		}
	}

	public Collection<TransportUpdate> getTransportUpdates(Connection txn,
			ContactId c) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT transportId, key, value, localVersion"
					+ " FROM transportProperties AS tp"
					+ " JOIN transportVersions AS tv"
					+ " ON tp.transportId = tv.transportId"
					+ " WHERE tv.contactId = ?"
					+ " AND localVersion > localAcked";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			List<TransportUpdate> updates = new ArrayList<TransportUpdate>();
			TransportId lastId = null;
			TransportProperties p = null;
			while(rs.next()) {
				TransportId id = new TransportId(rs.getBytes(1));
				String key = rs.getString(2), value = rs.getString(3);
				long version = rs.getLong(4);
				if(!id.equals(lastId)) {
					p = new TransportProperties();
					updates.add(new TransportUpdate(id, p, version));
				}
				p.put(key, value);
			}
			rs.close();
			ps.close();
			if(updates.isEmpty()) return null;
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
					+ " LEFT OUTER JOIN flags AS f"
					+ " ON m.messageId = f.messageId"
					+ " WHERE (NOT read) OR (read IS NULL)"
					+ " GROUP BY groupId";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			Map<GroupId, Integer> counts = new HashMap<GroupId, Integer>();
			while(rs.next()) {
				GroupId g = new GroupId(rs.getBytes(1));
				counts.put(g, rs.getInt(2));
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

	public boolean hasSendableMessages(Connection txn, ContactId c)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Do we have any sendable private messages?
			String sql = "SELECT m.messageId FROM messages AS m"
					+ " JOIN statuses AS s"
					+ " ON m.messageId = s.messageId"
					+ " WHERE m.contactId = ? AND status = ?"
					+ " LIMIT ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setShort(2, (short) Status.NEW.ordinal());
			ps.setInt(3, 1);
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
					+ " JOIN expiryVersions AS ev"
					+ " ON cg.contactId = ev.contactId"
					+ " JOIN statuses AS s"
					+ " ON m.messageId = s.messageId"
					+ " AND cg.contactId = s.contactId"
					+ " WHERE cg.contactId = ?"
					+ " AND timestamp >= expiry"
					+ " AND status = ?"
					+ " AND sendability > ZERO()"
					+ " LIMIT ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setShort(2, (short) Status.NEW.ordinal());
			ps.setInt(3, 1);
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
				return -1L;
			}
			long connection = rs.getLong(1);
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			// Increment the connection counter
			sql = "UPDATE secrets SET outgoing = outgoing + ?"
					+ " WHERE contactId = ? AND transportId = ? AND period = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, 1);
			ps.setInt(2, c.getInt());
			ps.setBytes(3, t.getBytes());
			ps.setLong(4, period);
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

	public void incrementExpiryVersions(Connection txn) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE expiryVersions"
					+ " SET localVersion = localVersion + ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, 1);
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
			// Set the status of each message to SEEN if it's currently SENT
			String sql = "UPDATE statuses SET status = ?"
					+ " WHERE messageId = ? AND contactId = ? AND status = ?";
			ps = txn.prepareStatement(sql);
			ps.setShort(1, (short) Status.SEEN.ordinal());
			ps.setInt(3, c.getInt());
			ps.setShort(4, (short) Status.SENT.ordinal());
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
			sql = "UPDATE groupVersions SET localVersion = localVersion + ?"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, 1);
			for(Integer c : visible) {
				ps.setInt(2, c);
				ps.addBatch();
			}
			int[] affectedBatch = ps.executeBatch();
			if(affectedBatch.length != visible.size())
				throw new DbStateException();
			for(int i = 0; i < affectedBatch.length; i++) {
				if(affectedBatch[i] != 1) throw new DbStateException();
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
			sql = "UPDATE groupVersions SET localVersion = localVersion + ?"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, 1);
			ps.setInt(2, c.getInt());
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
					+ " SET localVersion = localVersion + ?"
					+ " WHERE transportId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, 1);
			ps.setBytes(2, t.getBytes());
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

	public void setExpiryTime(Connection txn, ContactId c, long expiry,
			long version) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE expiryVersions"
					+ " SET expiry = ?, remoteVersion = ?, remoteAcked = FALSE"
					+ " WHERE contactId = ? AND remoteVersion < ?";
			ps = txn.prepareStatement(sql);
			ps.setLong(1, expiry);
			ps.setLong(2, version);
			ps.setInt(3, c.getInt());
			ps.setLong(4, version);
			int affected = ps.executeUpdate();
			if(affected > 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void setExpiryUpdateAcked(Connection txn, ContactId c, long version)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE expiryVersions SET localAcked = ?"
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

	public Rating setRating(Connection txn, AuthorId a, Rating r)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT rating FROM ratings WHERE authorId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, a.getBytes());
			rs = ps.executeQuery();
			Rating old;
			if(rs.next()) {
				// A rating row exists - update it if necessary
				old = Rating.values()[rs.getByte(1)];
				if(rs.next()) throw new DbStateException();
				rs.close();
				ps.close();
				if(!old.equals(r)) {
					sql = "UPDATE ratings SET rating = ? WHERE authorId = ?";
					ps = txn.prepareStatement(sql);
					ps.setShort(1, (short) r.ordinal());
					ps.setBytes(2, a.getBytes());
					int affected = ps.executeUpdate();
					if(affected != 1) throw new DbStateException();
					ps.close();
				}
			} else {
				// No rating row exists - create one if necessary
				rs.close();
				ps.close();
				old = Rating.UNRATED;
				if(!old.equals(r)) {
					sql = "INSERT INTO ratings (authorId, rating)"
							+ " VALUES (?, ?)";
					ps = txn.prepareStatement(sql);
					ps.setBytes(1, a.getBytes());
					ps.setShort(2, (short) r.ordinal());
					int affected = ps.executeUpdate();
					if(affected != 1) throw new DbStateException();
					ps.close();
				}
			}
			return old;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public boolean setRead(Connection txn, MessageId m, boolean read)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT read FROM flags WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			rs = ps.executeQuery();
			boolean old;
			if(rs.next()) {
				// A flag row exists - update it if necessary
				old = rs.getBoolean(1);
				if(rs.next()) throw new DbStateException();
				rs.close();
				ps.close();
				if(old != read) {
					sql = "UPDATE flags SET read = ? WHERE messageId = ?";
					ps = txn.prepareStatement(sql);
					ps.setBoolean(1, read);
					ps.setBytes(2, m.getBytes());
					int affected = ps.executeUpdate();
					if(affected != 1) throw new DbStateException();
					ps.close();
				}
			} else {
				// No flag row exists - create one if necessary
				ps.close();
				rs.close();
				old = false;
				if(old != read) {
					sql = "INSERT INTO flags (messageId, read, starred)"
							+ " VALUES (?, ?, ?)";
					ps = txn.prepareStatement(sql);
					ps.setBytes(1, m.getBytes());
					ps.setBoolean(2, read);
					ps.setBoolean(3, false);
					int affected = ps.executeUpdate();
					if(affected != 1) throw new DbStateException();
					ps.close();
				}
			}
			return old;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void setRemoteProperties(Connection txn, ContactId c,
			TransportUpdate u) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			TransportId t = u.getId();
			// Find the existing version, if any
			String sql = "SELECT remoteVersion FROM contactTransportVersions"
					+ " WHERE contactId = ? AND transportId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setBytes(2, t.getBytes());
			rs = ps.executeQuery();
			long version = rs.next() ? rs.getLong(1) : -1L;
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			// Mark the update as needing to be acked
			if(version == -1L) {
				// The row doesn't exist - create it
				sql = "INSERT INTO contactTransportVersions (contactId,"
						+ " transportId, remoteVersion, remoteAcked)"
						+ " VALUES (?, ?, ?, FALSE)";
				ps = txn.prepareStatement(sql);
				ps.setInt(1, c.getInt());
				ps.setBytes(2, t.getBytes());
				ps.setLong(3, u.getVersionNumber());
				int affected = ps.executeUpdate();
				if(affected != 1) throw new DbStateException();
				ps.close();
			} else {
				// The row exists - update it
				sql = "UPDATE contactTransportVersions"
						+ " SET remoteVersion = ?, remoteAcked = FALSE"
						+ " WHERE contactId = ? AND transportId = ?";
				ps = txn.prepareStatement(sql);
				ps.setLong(1, Math.max(version, u.getVersionNumber()));
				ps.setInt(1, c.getInt());
				ps.setBytes(2, t.getBytes());
				int affected = ps.executeUpdate();
				if(affected > 1) throw new DbStateException();
				ps.close();
				// Return if the update is obsolete
				if(u.getVersionNumber() <= version) return;
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
			TransportProperties p = u.getProperties();
			if(p.isEmpty()) return;
			sql = "INSERT INTO contactTransportProperties"
					+ " (contactId, transportId, key, value)"
					+ " VALUES (?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setBytes(2, t.getBytes());
			for(Entry<String, String> e : p.entrySet()) {
				ps.setString(1, e.getKey());
				ps.setString(2, e.getValue());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if(batchAffected.length != p.size()) throw new DbStateException();
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

	public void setSendability(Connection txn, MessageId m, int sendability)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE messages SET sendability = ?"
					+ " WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, sendability);
			ps.setBytes(2, m.getBytes());
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public boolean setStarred(Connection txn, MessageId m, boolean starred)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT starred FROM flags WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			rs = ps.executeQuery();
			boolean old;
			if(rs.next()) {
				// A flag row exists - update it if necessary
				old = rs.getBoolean(1);
				if(rs.next()) throw new DbStateException();
				rs.close();
				ps.close();
				if(old != starred) {
					sql = "UPDATE flags SET starred = ? WHERE messageId = ?";
					ps = txn.prepareStatement(sql);
					ps.setBoolean(1, starred);
					ps.setBytes(2, m.getBytes());
					int affected = ps.executeUpdate();
					if(affected != 1) throw new DbStateException();
					ps.close();
				}
			} else {
				// No flag row exists - create one if necessary
				ps.close();
				rs.close();
				old = false;
				if(old != starred) {
					sql = "INSERT INTO flags (messageId, read, starred)"
							+ " VALUES (?, ?, ?)";
					ps = txn.prepareStatement(sql);
					ps.setBytes(1, m.getBytes());
					ps.setBoolean(2, false);
					ps.setBoolean(3, starred);
					int affected = ps.executeUpdate();
					if(affected != 1) throw new DbStateException();
					ps.close();
				}
			}
			return old;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void setStatus(Connection txn, ContactId c, MessageId m, Status s)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT status FROM statuses"
					+ " WHERE messageId = ? AND contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setInt(2, c.getInt());
			rs = ps.executeQuery();
			if(rs.next()) {
				// A status row exists - update it if necessary
				Status old = Status.values()[rs.getByte(1)];
				if(rs.next()) throw new DbStateException();
				rs.close();
				ps.close();
				if(!old.equals(Status.SEEN) && !old.equals(s)) {
					sql = "UPDATE statuses SET status = ?"
							+ " WHERE messageId = ? AND contactId = ?";
					ps = txn.prepareStatement(sql);
					ps.setShort(1, (short) s.ordinal());
					ps.setBytes(2, m.getBytes());
					ps.setInt(3, c.getInt());
					int affected = ps.executeUpdate();
					if(affected != 1) throw new DbStateException();
					ps.close();
				}
			} else {
				// No status row exists - create one
				rs.close();
				ps.close();
				sql = "INSERT INTO statuses (messageId, contactId, status)"
						+ " VALUES (?, ?, ?)";
				ps = txn.prepareStatement(sql);
				ps.setBytes(1, m.getBytes());
				ps.setInt(2, c.getInt());
				ps.setShort(3, (short) s.ordinal());
				int affected = ps.executeUpdate();
				if(affected != 1) throw new DbStateException();
				ps.close();
			}
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
					+ " JOIN expiryVersions AS ev"
					+ " ON cg.contactId = ev.contactId"
					+ " WHERE messageId = ?"
					+ " AND cg.contactId = ?"
					+ " AND timestamp >= expiry";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setInt(2, c.getInt());
			rs = ps.executeQuery();
			boolean found = rs.next();
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			if(!found) return false;
			sql = "UPDATE statuses SET status = ?"
					+ " WHERE messageId = ? AND contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setShort(1, (short) Status.SEEN.ordinal());
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

	public void setSubscriptions(Connection txn, ContactId c,
			SubscriptionUpdate u) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Find the existing version
			String sql = "SELECT remoteVersion FROM groupVersions"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			if(!rs.next()) throw new DbStateException();
			long version = rs.getLong(1);
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			// Mark the update as needing to be acked
			sql = "UPDATE groupVersions"
					+ " SET remoteVersion = ?, remoteAcked = FALSE"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setLong(1, Math.max(version, u.getVersionNumber()));
			ps.setInt(2, c.getInt());
			int affected = ps.executeUpdate();
			if(affected > 1) throw new DbStateException();
			ps.close();
			// Delete the existing subscriptions, if any
			sql = "DELETE FROM contactGroups WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.executeUpdate();
			// Store the new subscriptions, if any
			Collection<Group> subs = u.getGroups();
			if(subs.isEmpty()) return;
			sql = "INSERT INTO contactGroups (contactId, groupId, name, key)"
					+ " VALUES (?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			for(Group g : subs) {
				ps.setBytes(2, g.getId().getBytes());
				ps.setString(3, g.getName());
				byte[] key = g.getPublicKey();
				if(key == null) ps.setNull(4, BINARY);
				else ps.setBytes(4, key);
				ps.addBatch();
			}
			int[] affectedBatch = ps.executeBatch();
			if(affectedBatch.length != subs.size())
				throw new DbStateException();
			for(int i = 0; i < affectedBatch.length; i++) {
				if(affectedBatch[i] != 1) throw new DbStateException();
			}
			ps.close();
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
}
