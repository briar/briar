package net.sf.briar.db;

import static net.sf.briar.db.DatabaseConstants.EXPIRY_MODULUS;
import static net.sf.briar.db.DatabaseConstants.RETRANSMIT_THRESHOLD;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.Rating;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.clock.Clock;
import net.sf.briar.api.db.ContactTransport;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.MessageHeader;
import net.sf.briar.api.db.Status;
import net.sf.briar.api.db.TemporarySecret;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupFactory;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Transport;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.util.FileUtils;

/**
 * A generic database implementation that can be used with any JDBC-compatible
 * database library. (Tested with H2, Derby and HSQLDB.)
 */
abstract class JdbcDatabase implements Database<Connection> {

	private static final String CREATE_SUBSCRIPTIONS =
			"CREATE TABLE subscriptions"
					+ " (groupId HASH NOT NULL,"
					+ " groupName VARCHAR NOT NULL,"
					+ " groupKey BINARY," // Null for unrestricted groups
					+ " start BIGINT NOT NULL,"
					+ " PRIMARY KEY (groupId))";

	private static final String CREATE_CONTACTS =
			"CREATE TABLE contacts"
					+ " (contactId COUNTER,"
					+ " PRIMARY KEY (contactId))";

	private static final String CREATE_MESSAGES =
			"CREATE TABLE messages"
					+ " (messageId HASH NOT NULL,"
					+ " parentId HASH," // Null for the first message in a thread
					+ " groupId HASH," // Null for private messages
					+ " authorId HASH," // Null for private or anonymous messages
					+ " subject VARCHAR NOT NULL,"
					+ " timestamp BIGINT NOT NULL,"
					+ " length INT NOT NULL,"
					+ " bodyStart INT NOT NULL,"
					+ " bodyLength INT NOT NULL,"
					+ " raw BLOB NOT NULL,"
					+ " sendability INT," // Null for private messages
					+ " contactId INT," // Null for group messages
					+ " PRIMARY KEY (messageId),"
					+ " FOREIGN KEY (groupId) REFERENCES subscriptions (groupId)"
					+ " ON DELETE CASCADE,"
					+ " FOREIGN KEY (contactId) REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE)";

	private static final String INDEX_MESSAGES_BY_PARENT =
			"CREATE INDEX messagesByParent ON messages (parentId)";

	private static final String INDEX_MESSAGES_BY_AUTHOR =
			"CREATE INDEX messagesByAuthor ON messages (authorId)";

	private static final String INDEX_MESSAGES_BY_TIMESTAMP =
			"CREATE INDEX messagesByTimestamp ON messages (timestamp)";

	private static final String INDEX_MESSAGES_BY_SENDABILITY =
			"CREATE INDEX messagesBySendability ON messages (sendability)";

	private static final String CREATE_VISIBILITIES =
			"CREATE TABLE visibilities"
					+ " (contactId INT NOT NULL,"
					+ " groupId HASH," // Null for the head of the linked list
					+ " nextId HASH," // Null for the tail of the linked list
					+ " deleted BIGINT NOT NULL,"
					+ " FOREIGN KEY (contactId) REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE,"
					+ " FOREIGN KEY (groupId) REFERENCES subscriptions (groupId)"
					+ " ON DELETE CASCADE)";

	private static final String INDEX_VISIBILITIES_BY_GROUP =
			"CREATE INDEX visibilitiesByGroup ON visibilities (groupId)";

	private static final String INDEX_VISIBILITIES_BY_NEXT =
			"CREATE INDEX visibilitiesByNext on visibilities (nextId)";

	private static final String CREATE_BATCHES_TO_ACK =
			"CREATE TABLE batchesToAck"
					+ " (batchId HASH NOT NULL,"
					+ " contactId INT NOT NULL,"
					+ " PRIMARY KEY (batchId, contactId),"
					+ " FOREIGN KEY (contactId) REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_CONTACT_SUBSCRIPTIONS =
			"CREATE TABLE contactSubscriptions"
					+ " (contactId INT NOT NULL,"
					+ " groupId HASH NOT NULL,"
					+ " groupName VARCHAR NOT NULL,"
					+ " groupKey BINARY," // Null for unrestricted groups
					+ " start BIGINT NOT NULL,"
					+ " PRIMARY KEY (contactId, groupId),"
					+ " FOREIGN KEY (contactId) REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_OUTSTANDING_BATCHES =
			"CREATE TABLE outstandingBatches"
					+ " (batchId HASH NOT NULL,"
					+ " contactId INT NOT NULL,"
					+ " timestamp BIGINT NOT NULL,"
					+ " passover INT NOT NULL,"
					+ " PRIMARY KEY (batchId, contactId),"
					+ " FOREIGN KEY (contactId) REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_OUTSTANDING_MESSAGES =
			"CREATE TABLE outstandingMessages"
					+ " (batchId HASH NOT NULL,"
					+ " contactId INT NOT NULL,"
					+ " messageId HASH NOT NULL,"
					+ " PRIMARY KEY (batchId, contactId, messageId),"
					+ " FOREIGN KEY (batchId, contactId)"
					+ " REFERENCES outstandingBatches (batchId, contactId)"
					+ " ON DELETE CASCADE,"
					+ " FOREIGN KEY (messageId) REFERENCES messages (messageId)"
					+ " ON DELETE CASCADE)";

	private static final String INDEX_OUTSTANDING_MESSAGES_BY_BATCH =
			"CREATE INDEX outstandingMessagesByBatch"
					+ " ON outstandingMessages (batchId)";

	private static final String CREATE_RATINGS =
			"CREATE TABLE ratings"
					+ " (authorId HASH NOT NULL,"
					+ " rating SMALLINT NOT NULL,"
					+ " PRIMARY KEY (authorId))";

	private static final String CREATE_STATUSES =
			"CREATE TABLE statuses"
					+ " (messageId HASH NOT NULL,"
					+ " contactId INT NOT NULL,"
					+ " status SMALLINT NOT NULL,"
					+ " PRIMARY KEY (messageId, contactId),"
					+ " FOREIGN KEY (messageId) REFERENCES messages (messageId)"
					+ " ON DELETE CASCADE,"
					+ " FOREIGN KEY (contactId) REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE)";

	private static final String INDEX_STATUSES_BY_MESSAGE =
			"CREATE INDEX statusesByMessage ON statuses (messageId)";

	private static final String INDEX_STATUSES_BY_CONTACT =
			"CREATE INDEX statusesByContact ON statuses (contactId)";

	private static final String CREATE_TRANSPORT_CONFIGS =
			"CREATE TABLE transportConfigs"
					+ " (transportId HASH NOT NULL,"
					+ " key VARCHAR NOT NULL,"
					+ " value VARCHAR NOT NULL,"
					+ " PRIMARY KEY (transportId, key))";

	private static final String CREATE_TRANSPORT_PROPS =
			"CREATE TABLE transportProperties"
					+ " (transportId HASH NOT NULL,"
					+ " key VARCHAR NOT NULL,"
					+ " value VARCHAR NOT NULL,"
					+ " PRIMARY KEY (transportId, key))";

	private static final String CREATE_CONTACT_TRANSPORT_PROPS =
			"CREATE TABLE contactTransportProperties"
					+ " (contactId INT NOT NULL,"
					+ " transportId HASH NOT NULL,"
					+ " key VARCHAR NOT NULL,"
					+ " value VARCHAR NOT NULL,"
					+ " PRIMARY KEY (contactId, transportId, key),"
					+ " FOREIGN KEY (contactId) REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_CONTACT_TRANSPORTS =
			"CREATE TABLE contactTransports"
					+ " (contactId INT NOT NULL,"
					+ " transportId HASH NOT NULL,"
					+ " epoch BIGINT NOT NULL,"
					+ " clockDiff BIGINT NOT NULL,"
					+ " latency BIGINT NOT NULL,"
					+ " alice BOOLEAN NOT NULL,"
					+ " PRIMARY KEY (contactId, transportId),"
					+ " FOREIGN KEY (contactId) REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE)";

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
					+ " FOREIGN KEY (contactId) REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_SUBSCRIPTION_TIMES =
			"CREATE TABLE subscriptionTimes"
					+ " (contactId INT NOT NULL,"
					+ " received BIGINT NOT NULL,"
					+ " acked BIGINT NOT NULL,"
					+ " expiry BIGINT NOT NULL,"
					+ " PRIMARY KEY (contactId),"
					+ " FOREIGN KEY (contactId) REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_TRANSPORT_TIMESTAMPS =
			"CREATE TABLE transportTimestamps"
					+ " (contactId INT NOT NULL,"
					+ " sent BIGINT NOT NULL,"
					+ " received BIGINT NOT NULL,"
					+ " modified BIGINT NOT NULL,"
					+ " PRIMARY KEY (contactId),"
					+ " FOREIGN KEY (contactId) REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_FLAGS =
			"CREATE TABLE flags"
					+ " (messageId HASH NOT NULL,"
					+ " read BOOLEAN NOT NULL,"
					+ " starred BOOLEAN NOT NULL,"
					+ " PRIMARY KEY (messageId),"
					+ " FOREIGN KEY (messageId) REFERENCES messages (messageId)"
					+ " ON DELETE CASCADE)";

	private static final Logger LOG =
			Logger.getLogger(JdbcDatabase.class.getName());

	// FIXME: Can this factory be done away with?
	private final GroupFactory groupFactory;
	private final Clock clock;
	// Different database libraries use different names for certain types
	private final String hashType, binaryType, counterType, secretType;

	private final LinkedList<Connection> connections =
			new LinkedList<Connection>(); // Locking: self

	private int openConnections = 0; // Locking: connections
	private boolean closed = false; // Locking: connections

	protected abstract Connection createConnection() throws SQLException;

	JdbcDatabase(GroupFactory groupFactory, Clock clock, String hashType,
			String binaryType, String counterType, String secretType) {
		this.groupFactory = groupFactory;
		this.clock = clock;
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
			s.executeUpdate(insertTypeNames(CREATE_SUBSCRIPTIONS));
			s.executeUpdate(insertTypeNames(CREATE_CONTACTS));
			s.executeUpdate(insertTypeNames(CREATE_MESSAGES));
			s.executeUpdate(INDEX_MESSAGES_BY_PARENT);
			s.executeUpdate(INDEX_MESSAGES_BY_AUTHOR);
			s.executeUpdate(INDEX_MESSAGES_BY_TIMESTAMP);
			s.executeUpdate(INDEX_MESSAGES_BY_SENDABILITY);
			s.executeUpdate(insertTypeNames(CREATE_VISIBILITIES));
			s.executeUpdate(INDEX_VISIBILITIES_BY_GROUP);
			s.executeUpdate(INDEX_VISIBILITIES_BY_NEXT);
			s.executeUpdate(insertTypeNames(CREATE_BATCHES_TO_ACK));
			s.executeUpdate(insertTypeNames(CREATE_CONTACT_SUBSCRIPTIONS));
			s.executeUpdate(insertTypeNames(CREATE_OUTSTANDING_BATCHES));
			s.executeUpdate(insertTypeNames(CREATE_OUTSTANDING_MESSAGES));
			s.executeUpdate(INDEX_OUTSTANDING_MESSAGES_BY_BATCH);
			s.executeUpdate(insertTypeNames(CREATE_RATINGS));
			s.executeUpdate(insertTypeNames(CREATE_STATUSES));
			s.executeUpdate(INDEX_STATUSES_BY_MESSAGE);
			s.executeUpdate(INDEX_STATUSES_BY_CONTACT);
			s.executeUpdate(insertTypeNames(CREATE_TRANSPORT_CONFIGS));
			s.executeUpdate(insertTypeNames(CREATE_TRANSPORT_PROPS));
			s.executeUpdate(insertTypeNames(CREATE_CONTACT_TRANSPORT_PROPS));
			s.executeUpdate(insertTypeNames(CREATE_CONTACT_TRANSPORTS));
			s.executeUpdate(insertTypeNames(CREATE_SECRETS));
			s.executeUpdate(insertTypeNames(CREATE_SUBSCRIPTION_TIMES));
			s.executeUpdate(insertTypeNames(CREATE_TRANSPORT_TIMESTAMPS));
			s.executeUpdate(insertTypeNames(CREATE_FLAGS));
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
			if(LOG.isLoggable(Level.WARNING))LOG.warning(e.toString());
		}
	}

	private void tryToClose(ResultSet rs) {
		if(rs != null) try {
			rs.close();
		} catch(SQLException e) {
			if(LOG.isLoggable(Level.WARNING))LOG.warning(e.toString());
		}
	}

	public Connection startTransaction() throws DbException {
		Connection txn = null;
		synchronized(connections) {
			if(closed) throw new DbException();
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
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
			try {
				txn.close();
			} catch(SQLException e1) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e1.toString());
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
					if(LOG.isLoggable(Level.INFO))
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

	public void addBatchToAck(Connection txn, ContactId c, BatchId b)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT NULL FROM batchesToAck"
					+ " WHERE batchId = ? AND contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, b.getBytes());
			ps.setInt(2, c.getInt());
			rs = ps.executeQuery();
			boolean found = rs.next();
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			if(found) return;
			sql = "INSERT INTO batchesToAck (batchId, contactId)"
					+ " VALUES (?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, b.getBytes());
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

	public ContactId addContact(Connection txn) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Create a new contact row
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
			// Create the head-of-list pointer for the visibility list
			sql = "INSERT INTO visibilities (contactId, deleted)"
					+ " VALUES (?, ZERO())";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			// Initialise the subscription timestamps
			sql = "INSERT INTO subscriptionTimes"
					+ " (contactId, received, acked, expiry)"
					+ " VALUES (?, ZERO(), ZERO(), ZERO())";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			// Initialise the transport timestamps
			sql = "INSERT INTO transportTimestamps"
					+ " (contactId, sent, received, modified)"
					+ " VALUES (?, ZERO(), ZERO(), ZERO())";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
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
			String sql = "INSERT INTO contactTransports"
					+ " (contactId, transportId, epoch, clockDiff, latency,"
					+ " alice)"
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
			if(m.getParent() == null) ps.setNull(2, Types.BINARY);
			else ps.setBytes(2, m.getParent().getBytes());
			ps.setBytes(3, m.getGroup().getBytes());
			if(m.getAuthor() == null) ps.setNull(4, Types.BINARY);
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

	public void addOutstandingBatch(Connection txn, ContactId c, BatchId b,
			Collection<MessageId> sent) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Create an outstanding batch row
			String sql = "INSERT INTO outstandingBatches"
					+ " (batchId, contactId, timestamp, passover)"
					+ " VALUES (?, ?, ?, ZERO())";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, b.getBytes());
			ps.setInt(2, c.getInt());
			ps.setLong(3, clock.currentTimeMillis());
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			// Create an outstanding message row for each message in the batch
			sql = "INSERT INTO outstandingMessages"
					+ " (batchId, contactId, messageId)"
					+ " VALUES (?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, b.getBytes());
			ps.setInt(2, c.getInt());
			for(MessageId m : sent) {
				ps.setBytes(3, m.getBytes());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if(batchAffected.length != sent.size())
				throw new DbStateException();
			for(int i = 0; i < batchAffected.length; i++) {
				if(batchAffected[i] != 1) throw new DbStateException();
			}
			ps.close();
			// Set the status of each message in the batch to SENT
			sql = "UPDATE statuses SET status = ?"
					+ " WHERE messageId = ? AND contactId = ? AND status = ?";
			ps = txn.prepareStatement(sql);
			ps.setShort(1, (short) Status.SENT.ordinal());
			ps.setInt(3, c.getInt());
			ps.setShort(4, (short) Status.NEW.ordinal());
			for(MessageId m : sent) {
				ps.setBytes(2, m.getBytes());
				ps.addBatch();
			}
			batchAffected = ps.executeBatch();
			if(batchAffected.length != sent.size())
				throw new DbStateException();
			for(int i = 0; i < batchAffected.length; i++) {
				if(batchAffected[i] > 1) throw new DbStateException();
			}
			ps.close();
		} catch(SQLException e) {
			tryToClose(rs);
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
			if(m.getParent() == null) ps.setNull(2, Types.BINARY);
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
			String sql = "INSERT INTO subscriptions"
					+ " (groupId, groupName, groupKey, start)"
					+ " VALUES (?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getId().getBytes());
			ps.setString(2, g.getName());
			ps.setBytes(3, g.getPublicKey());
			long now = clock.currentTimeMillis();
			ps.setLong(4, now);
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
			String sql = "INSERT INTO secrets"
					+ " (contactId, transportId, period, secret, outgoing,"
					+ " centre, bitmap)"
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

	public void addSubscription(Connection txn, ContactId c, Group g,
			long start) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Check whether the subscription already exists
			String sql = "SELECT NULL FROM contactSubscriptions"
					+ " WHERE contactId = ? AND groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setBytes(2, g.getId().getBytes());
			rs = ps.executeQuery();
			boolean found = rs.next();
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			if(found) return;
			// Add the subscription
			sql = "INSERT INTO contactSubscriptions"
					+ " (contactId, groupId, groupName, groupKey, start)"
					+ " VALUES (?, ?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setBytes(2, g.getId().getBytes());
			ps.setString(3, g.getName());
			ps.setBytes(4, g.getPublicKey());
			ps.setLong(5, start);
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void addVisibility(Connection txn, ContactId c, GroupId g)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Find the new element's predecessor
			byte[] groupId = null, nextId = null;
			long deleted = 0L;
			String sql = "SELECT groupId, nextId, deleted FROM visibilities"
					+ " WHERE contactId = ? AND nextId > ?"
					+ " ORDER BY nextId LIMIT ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setBytes(2, g.getBytes());
			ps.setInt(3, 1);
			rs = ps.executeQuery();
			if(!rs.next()) {
				// The predecessor has a null nextId so it's at the tail
				rs.close();
				ps.close();
				sql = "SELECT groupId, nextId, deleted FROM visibilities"
						+ " WHERE contactId = ? AND nextId IS NULL";
				ps = txn.prepareStatement(sql);
				ps.setInt(1, c.getInt());
				rs = ps.executeQuery();
				if(!rs.next()) throw new DbStateException();				
			}
			groupId = rs.getBytes(1);
			nextId = rs.getBytes(2);
			deleted = rs.getLong(3);
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			// Update the predecessor's nextId
			if(groupId == null) {
				// Inserting at the head of the list
				sql = "UPDATE visibilities SET nextId = ?"
						+ " WHERE contactId = ? AND groupId IS NULL";
				ps = txn.prepareStatement(sql);
				ps.setBytes(1, g.getBytes());
				ps.setInt(2, c.getInt());
			} else {
				// Inserting in the middle or at the tail of the list
				sql = "UPDATE visibilities SET nextId = ?"
						+ " WHERE contactId = ? AND groupId = ?";
				ps = txn.prepareStatement(sql);
				ps.setBytes(1, g.getBytes());
				ps.setInt(2, c.getInt());
				ps.setBytes(3, groupId);
			}
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			// Insert the new element
			sql = "INSERT INTO visibilities"
					+ " (contactId, groupId, nextId, deleted)"
					+ " VALUES (?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setBytes(2, g.getBytes());
			if(nextId == null) ps.setNull(3, Types.BINARY); // At the tail
			else ps.setBytes(3, nextId); // In the middle
			ps.setLong(4, deleted);
			affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
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
			String sql = "SELECT NULL FROM subscriptions WHERE groupId = ?";
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

	public boolean containsSubscription(Connection txn, GroupId g, long time)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT NULL FROM subscriptions"
					+ " WHERE groupId = ? AND start <= ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			ps.setLong(2, time);
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

	public boolean containsVisibleSubscription(Connection txn, GroupId g,
			ContactId c, long time) throws DbException {
		boolean found = false;
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT start FROM subscriptions AS s"
					+ " JOIN visibilities AS v"
					+ " ON s.groupId = v.groupId"
					+ " WHERE s.groupId = ? AND contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			ps.setInt(2, c.getInt());
			rs = ps.executeQuery();
			if(rs.next()) {
				long start = rs.getLong(1);
				if(start <= time) found = true;
				if(rs.next()) throw new DbStateException();
			}
			rs.close();
			ps.close();
			return found;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public Collection<BatchId> getBatchesToAck(Connection txn, ContactId c,
			int maxBatches) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT batchId FROM batchesToAck"
					+ " WHERE contactId = ?"
					+ " LIMIT ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setInt(2, maxBatches);
			rs = ps.executeQuery();
			List<BatchId> ids = new ArrayList<BatchId>();
			while(rs.next()) ids.add(new BatchId(rs.getBytes(1)));
			rs.close();
			ps.close();
			return Collections.unmodifiableList(ids);
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

	public long getExpiryTime(Connection txn) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			long timestamp = 0L;
			String sql = "SELECT timestamp FROM messages"
					+ " ORDER BY timestamp LIMIT ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, 1);
			rs = ps.executeQuery();
			if(rs.next()) {
				timestamp = rs.getLong(1);
				timestamp -= timestamp % EXPIRY_MODULUS;
			}
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return timestamp;
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

	public Collection<Transport> getLocalTransports(Connection txn)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT transportId, key, value"
					+ " FROM transportProperties"
					+ " ORDER BY transportId";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			List<Transport> transports = new ArrayList<Transport>();
			TransportId lastId = null;
			Transport t = null;
			while(rs.next()) {
				TransportId id = new TransportId(rs.getBytes(1));
				if(!id.equals(lastId)) {
					t = new Transport(id);
					transports.add(t);
				}
				t.put(rs.getString(2), rs.getString(3));
			}
			rs.close();
			ps.close();
			return Collections.unmodifiableList(transports);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public Collection<BatchId> getLostBatches(Connection txn, ContactId c)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT batchId FROM outstandingBatches"
					+ " WHERE contactId = ? AND passover >= ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setInt(2, RETRANSMIT_THRESHOLD);
			rs = ps.executeQuery();
			List<BatchId> ids = new ArrayList<BatchId>();
			while(rs.next()) ids.add(new BatchId(rs.getBytes(1)));
			rs.close();
			ps.close();
			return Collections.unmodifiableList(ids);
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
					+ " JOIN contactSubscriptions AS cs"
					+ " ON m.groupId = cs.groupId"
					+ " JOIN visibilities AS v"
					+ " ON m.groupId = v.groupId"
					+ " AND cs.contactId = v.contactId"
					+ " JOIN statuses AS s"
					+ " ON m.messageId = s.messageId"
					+ " AND cs.contactId = s.contactId"
					+ " JOIN subscriptionTimes AS st"
					+ " ON cs.contactId = st.contactId"
					+ " WHERE m.messageId = ?"
					+ " AND cs.contactId = ?"
					+ " AND timestamp >= start"
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
			sql = "SELECT COUNT(messageId) FROM messages"
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

	public boolean getRead(Connection txn, MessageId m) throws DbException {
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
				if(!id.equals(lastId)) {
					p = new TransportProperties();
					properties.put(id, p);
				}
				p.put(rs.getString(2), rs.getString(3));
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
			String sql = "SELECT contactId, transportId, period, secret,"
					+ " outgoing, centre, bitmap"
					+ " FROM secrets";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			List<TemporarySecret> secrets = new ArrayList<TemporarySecret>();
			while(rs.next()) {
				ContactId c = new ContactId(rs.getInt(1));
				TransportId t = new TransportId(rs.getBytes(2));
				long period = rs.getLong(3);
				byte[] secret = rs.getBytes(4);
				long outgoing = rs.getLong(5);
				long centre = rs.getLong(6);
				byte[] bitmap = rs.getBytes(7);
				secrets.add(new TemporarySecret(c, t, period, secret, outgoing,
						centre, bitmap));
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

	public Collection<MessageId> getOfferableMessages(Connection txn,
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
					+ " JOIN contactSubscriptions AS cs"
					+ " ON m.groupId = cs.groupId"
					+ " JOIN visibilities AS v"
					+ " ON m.groupId = v.groupId"
					+ " AND cs.contactId = v.contactId"
					+ " JOIN statuses AS s"
					+ " ON m.messageId = s.messageId"
					+ " AND cs.contactId = s.contactId"
					+ " JOIN subscriptionTimes AS st"
					+ " ON cs.contactId = st.contactId"
					+ " WHERE cs.contactId = ?"
					+ " AND timestamp >= start"
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

	public Collection<MessageId> getSendableMessages(Connection txn,
			ContactId c, int capacity) throws DbException {
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
				if(total + length > capacity) break;
				ids.add(new MessageId(rs.getBytes(2)));
				total += length;
			}
			rs.close();
			ps.close();
			if(total == capacity) return Collections.unmodifiableList(ids);
			// Do we have any sendable group messages?
			sql = "SELECT length, m.messageId FROM messages AS m"
					+ " JOIN contactSubscriptions AS cs"
					+ " ON m.groupId = cs.groupId"
					+ " JOIN visibilities AS v"
					+ " ON m.groupId = v.groupId"
					+ " AND cs.contactId = v.contactId"
					+ " JOIN statuses AS s"
					+ " ON m.messageId = s.messageId"
					+ " AND cs.contactId = s.contactId"
					+ " JOIN subscriptionTimes AS st"
					+ " ON cs.contactId = st.contactId"
					+ " WHERE cs.contactId = ?"
					+ " AND timestamp >= start"
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

	public boolean getStarred(Connection txn, MessageId m) throws DbException {
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
			String sql = "SELECT groupId, groupName, groupKey"
					+ " FROM subscriptions";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			List<Group> subs = new ArrayList<Group>();
			while(rs.next()) {
				GroupId id = new GroupId(rs.getBytes(1));
				String name = rs.getString(2);
				byte[] publicKey = rs.getBytes(3);
				subs.add(groupFactory.createGroup(id, name, publicKey));
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
			String sql = "SELECT groupId, groupName, groupKey"
					+ " FROM contactSubscriptions"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			List<Group> subs = new ArrayList<Group>();
			while(rs.next()) {
				GroupId id = new GroupId(rs.getBytes(1));
				String name = rs.getString(2);
				byte[] publicKey = rs.getBytes(3);
				subs.add(groupFactory.createGroup(id, name, publicKey));
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

	public long getTransportsModified(Connection txn) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT DISTINCT modified FROM transportTimestamps";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			if(!rs.next()) throw new DbException();
			long modified = rs.getLong(1);
			if(rs.next()) throw new DbException();
			rs.close();
			ps.close();
			return modified;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public long getTransportsSent(Connection txn, ContactId c)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT sent FROM transportTimestamps"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			if(!rs.next()) throw new DbException();
			long sent = rs.getLong(1);
			if(rs.next()) throw new DbException();
			rs.close();
			ps.close();
			return sent;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
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
			String sql = "SELECT contactId FROM visibilities WHERE groupId = ?";
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

	public Map<GroupId, GroupId> getVisibleHoles(Connection txn, ContactId c,
			long timestamp) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT groupId, nextId FROM visibilities AS v"
					+ " JOIN subscriptionTimes AS st"
					+ " ON v.contactId = st.contactId"
					+ " WHERE v.contactId = ?"
					+ " AND deleted > acked AND deleted < ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setLong(2, timestamp);
			rs = ps.executeQuery();
			Map<GroupId, GroupId> holes = null;
			while(rs.next()) {
				byte[] b = rs.getBytes(1);
				GroupId groupId = b == null ? null : new GroupId(b);
				b = rs.getBytes(2);
				GroupId nextId = b == null ? null : new GroupId(b);
				if(holes == null) holes = new HashMap<GroupId, GroupId>();
				holes.put(groupId, nextId);
			}
			rs.close();
			ps.close();
			if(holes == null) return Collections.emptyMap();
			return Collections.unmodifiableMap(holes);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public Map<Group, Long> getVisibleSubscriptions(Connection txn, ContactId c,
			long timestamp) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT s.groupId, groupName, groupKey, start"
					+ " FROM subscriptions AS s"
					+ " JOIN visibilities AS v"
					+ " ON s.groupId = v.groupId"
					+ " JOIN subscriptionTimes AS st"
					+ " ON v.contactId = st.contactId"
					+ " WHERE v.contactId = ?"
					+ " AND start > acked AND start < ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setLong(2, timestamp);
			rs = ps.executeQuery();
			Map<Group, Long> subs = null;
			while(rs.next()) {
				GroupId id = new GroupId(rs.getBytes(1));
				String name = rs.getString(2);
				byte[] publicKey = rs.getBytes(3);
				long start = rs.getLong(4);
				if(subs == null) subs = new HashMap<Group, Long>();
				subs.put(groupFactory.createGroup(id, name, publicKey), start);
			}
			rs.close();
			ps.close();
			if(subs == null) return Collections.emptyMap();
			return Collections.unmodifiableMap(subs);
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
					+ " JOIN contactSubscriptions AS cs"
					+ " ON m.groupId = cs.groupId"
					+ " JOIN visibilities AS v"
					+ " ON m.groupId = v.groupId"
					+ " AND cs.contactId = v.contactId"
					+ " JOIN statuses AS s"
					+ " ON m.messageId = s.messageId"
					+ " AND cs.contactId = s.contactId"
					+ " JOIN subscriptionTimes AS st"
					+ " ON cs.contactId = st.contactId"
					+ " WHERE cs.contactId = ?"
					+ " AND timestamp >= start"
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

	public void incrementConnectionCounter(Connection txn, ContactId c,
			TransportId t, long period) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE secrets SET outgoing = outgoing + 1"
					+ " WHERE contactId = ? AND transportId = ? AND period = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setBytes(2, t.getBytes());
			ps.setLong(3, period);
			int affected = ps.executeUpdate();
			if(affected > 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void removeAckedBatch(Connection txn, ContactId c, BatchId b)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT timestamp FROM outstandingBatches"
					+ " WHERE contactId = ? AND batchId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setBytes(2, b.getBytes());
			rs = ps.executeQuery();
			if(!rs.next()) throw new DbStateException();
			long timestamp = rs.getLong(1);
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			// Increment the passover count of all older outstanding batches
			sql = "UPDATE outstandingBatches SET passover = passover + ?"
					+ " WHERE contactId = ? AND timestamp < ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, 1);
			ps.setInt(2, c.getInt());
			ps.setLong(3, timestamp);
			ps.executeUpdate();
			ps.close();
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
		removeBatch(txn, c, b, Status.SEEN);
	}

	private void removeBatch(Connection txn, ContactId c, BatchId b,
			Status newStatus) throws DbException {
		PreparedStatement ps = null, ps1 = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT messageId FROM outstandingMessages"
					+ " WHERE contactId = ? AND batchId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setBytes(2, b.getBytes());
			rs = ps.executeQuery();
			sql = "UPDATE statuses SET status = ?"
					+ " WHERE messageId = ? AND contactId = ? AND status = ?";
			ps1 = txn.prepareStatement(sql);
			ps1.setShort(1, (short) newStatus.ordinal());
			ps1.setInt(3, c.getInt());
			ps1.setShort(4, (short) Status.SENT.ordinal());
			int messages = 0;
			while(rs.next()) {
				messages++;
				ps1.setBytes(2, rs.getBytes(1));
				ps1.addBatch();
			}
			rs.close();
			ps.close();
			int[] batchAffected = ps1.executeBatch();
			if(batchAffected.length != messages) throw new DbStateException();
			for(int i = 0; i < batchAffected.length; i++) {
				if(batchAffected[i] > 1) throw new DbStateException();
			}
			ps1.close();
			// Cascade on delete
			sql = "DELETE FROM outstandingBatches WHERE batchId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, b.getBytes());
			int affected = ps.executeUpdate();
			if(affected > 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			tryToClose(ps1);
			throw new DbException(e);
		}
	}

	public void removeBatchesToAck(Connection txn, ContactId c,
			Collection<BatchId> sent) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "DELETE FROM batchesToAck"
					+ " WHERE contactId = ? and batchId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			for(BatchId b : sent) {
				ps.setBytes(2, b.getBytes());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if(batchAffected.length != sent.size())
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

	public void removeLostBatch(Connection txn, ContactId c, BatchId b)
			throws DbException {
		removeBatch(txn, c, b, Status.NEW);
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
		PreparedStatement ps = null, ps1 = null;
		ResultSet rs = null;
		try {
			// Remove the group ID from the visibility lists
			long now = clock.currentTimeMillis();
			String sql = "SELECT contactId, nextId FROM visibilities"
					+ " WHERE groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			rs = ps.executeQuery();
			while(rs.next()) {
				int contactId = rs.getInt(1);
				byte[] nextId = rs.getBytes(2);
				sql = "UPDATE visibilities SET nextId = ?, deleted = ?"
						+ " WHERE contactId = ? AND nextId = ?";
				ps1 = txn.prepareStatement(sql);
				if(nextId == null) ps1.setNull(1, Types.BINARY); // At the tail
				else ps1.setBytes(1, nextId); // At the head or in the middle
				ps1.setLong(2, now);
				ps1.setInt(3, contactId);
				ps1.setBytes(4, g.getBytes());
				int affected = ps1.executeUpdate();
				if(affected != 1) throw new DbStateException();
				ps1.close();
			}
			rs.close();
			ps.close();
			// Remove the group from the subscriptions table
			sql = "DELETE FROM subscriptions WHERE groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			e.printStackTrace();
			tryToClose(rs);
			tryToClose(ps);
			tryToClose(ps1);
			throw new DbException(e);
		}
	}

	public void removeSubscriptions(Connection txn, ContactId c, GroupId start,
			GroupId end) throws DbException {
		PreparedStatement ps = null;
		try {
			if(start == null && end == null) {
				// Delete everything
				String sql = "DELETE FROM contactSubscriptions"
						+ " WHERE contactId = ?";
				ps = txn.prepareStatement(sql);
				ps.setInt(1, c.getInt());
			} else if(start == null) {
				// Delete everything before end
				String sql = "DELETE FROM contactSubscriptions"
						+ " WHERE contactId = ? AND groupId < ?";
				ps = txn.prepareStatement(sql);
				ps.setInt(1, c.getInt());
				ps.setBytes(2, end.getBytes());
			} else if(end == null) {
				// Delete everything after start
				String sql = "DELETE FROM contactSubscriptions"
						+ " WHERE contactId = ? AND groupId > ?";
				ps = txn.prepareStatement(sql);
				ps.setInt(1, c.getInt());
				ps.setBytes(2, start.getBytes());
			} else {
				// Delete everything between start and end
				String sql = "DELETE FROM contactSubscriptions"
						+ " WHERE contactId = ?"
						+ " AND groupId > ? AND groupId < ?";
				ps = txn.prepareStatement(sql);
				ps.setInt(1, c.getInt());
				ps.setBytes(2, start.getBytes());
				ps.setBytes(3, end.getBytes());
			}
			ps.executeUpdate();
			ps.close();
		} catch(SQLException e) {
			e.printStackTrace();
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void removeVisibility(Connection txn, ContactId c, GroupId g)
			throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Remove the group ID from the linked list
			String sql = "SELECT nextId FROM visibilities"
					+ " WHERE contactId = ? AND groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setBytes(2, g.getBytes());
			rs = ps.executeQuery();
			if(!rs.next()) throw new DbStateException();
			byte[] nextId = rs.getBytes(1);
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			sql = "DELETE FROM visibilities"
					+ " WHERE contactId = ? AND groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setBytes(2, g.getBytes());
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			sql = "UPDATE visibilities SET nextId = ?, deleted = ?"
					+ " WHERE contactId = ? AND nextId = ?";
			ps = txn.prepareStatement(sql);
			if(nextId == null) ps.setNull(1, Types.BINARY); // At the tail
			else ps.setBytes(1, nextId); // At the head or in the middle
			ps.setLong(2, clock.currentTimeMillis());
			ps.setInt(3, c.getInt());
			ps.setBytes(4, g.getBytes());
			affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void setConfig(Connection txn, TransportId t, TransportConfig c)
			throws DbException {
		PreparedStatement ps = null;
		try {
			// Delete any existing config for the given transport
			String sql = "DELETE FROM transportConfigs WHERE transportId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, t.getBytes());
			ps.executeUpdate();
			ps.close();
			// Store the new config
			sql = "INSERT INTO transportConfigs (transportId, key, value)"
					+ " VALUES (?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, t.getBytes());
			for(Entry<String, String> e : c.entrySet()) {
				ps.setString(2, e.getKey());
				ps.setString(3, e.getValue());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if(batchAffected.length != c.size()) throw new DbStateException();
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

	public void setExpiryTime(Connection txn, ContactId c, long expiry)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE subscriptionTimes SET expiry = ?"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setLong(1, expiry);
			ps.setInt(2, c.getInt());
			int affected = ps.executeUpdate();
			if(affected > 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void setLocalProperties(Connection txn, TransportId t,
			TransportProperties p) throws DbException {
		PreparedStatement ps = null;
		try {
			// Delete any existing properties for the given transport
			String sql = "DELETE FROM transportProperties"
					+ " WHERE transportId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, t.getBytes());
			ps.executeUpdate();
			ps.close();
			// Store the new properties
			sql = "INSERT INTO transportProperties (transportId, key, value)"
					+ " VALUES (?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, t.getBytes());
			for(Entry<String, String> e : p.entrySet()) {
				ps.setString(2, e.getKey());
				ps.setString(3, e.getValue());
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
					+ " JOIN contactSubscriptions AS cs"
					+ " ON m.groupId = cs.groupId"
					+ " JOIN visibilities AS v"
					+ " ON m.groupId = v.groupId"
					+ " AND cs.contactId = v.contactId"
					+ " JOIN subscriptionTimes AS st"
					+ " ON cs.contactId = st.contactId"
					+ " WHERE messageId = ?"
					+ " AND cs.contactId = ?"
					+ " AND timestamp >= start"
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

	public void setSubscriptionsAcked(Connection txn, ContactId c,
			long timestamp) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE subscriptionTimes SET acked = ?"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setLong(1, timestamp);
			ps.setInt(2, c.getInt());
			int affected = ps.executeUpdate();
			if(affected > 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void setSubscriptionsReceived(Connection txn, ContactId c,
			long timestamp) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE subscriptionTimes SET received = ?"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setLong(1, timestamp);
			ps.setInt(2, c.getInt());
			int affected = ps.executeUpdate();
			if(affected > 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void setTransports(Connection txn, ContactId c,
			Collection<Transport> transports, long timestamp)
					throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Return if the timestamp isn't fresh
			String sql = "SELECT received FROM transportTimestamps"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			if(!rs.next()) throw new DbStateException();
			long lastTimestamp = rs.getLong(1);
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			if(lastTimestamp >= timestamp) return;
			// Delete any existing transport properties
			sql = "DELETE FROM contactTransportProperties WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.executeUpdate();
			ps.close();
			// Store the new transport properties
			sql = "INSERT INTO contactTransportProperties"
					+ " (contactId, transportId, key, value)"
					+ " VALUES (?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			int batchSize = 0;
			for(Transport t : transports) {
				ps.setBytes(2, t.getId().getBytes());
				for(Entry<String, String> e1 : t.entrySet()) {
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
			// Update the timestamp
			sql = "UPDATE transportTimestamps SET received = ?"
					+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setLong(1, timestamp);
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

	public void setTransportsModified(Connection txn, long timestamp)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE transportTimestamps set modified = ?";
			ps = txn.prepareStatement(sql);
			ps.setLong(1, timestamp);
			ps.executeUpdate();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void setTransportsSent(Connection txn, ContactId c, long timestamp)
			throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE transportTimestamps SET sent = ?"
					+ " WHERE contactId = ? AND sent < ?";
			ps = txn.prepareStatement(sql);
			ps.setLong(1, timestamp);
			ps.setInt(2, c.getInt());
			ps.setLong(3, timestamp);
			int affected = ps.executeUpdate();
			if(affected > 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}
}
