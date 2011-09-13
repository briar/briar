package net.sf.briar.db;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.Rating;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.Status;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupFactory;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.transport.ConnectionWindow;
import net.sf.briar.api.transport.ConnectionWindowFactory;
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
		+ " groupKey BINARY,"
		+ " start BIGINT NOT NULL,"
		+ " PRIMARY KEY (groupId))";

	private static final String CREATE_CONTACTS =
		"CREATE TABLE contacts"
		+ " (contactId INT NOT NULL,"
		+ " secret BINARY NOT NULL,"
		+ " PRIMARY KEY (contactId))";

	private static final String CREATE_MESSAGES =
		"CREATE TABLE messages"
		+ " (messageId HASH NOT NULL,"
		+ " parentId HASH,"
		+ " groupId HASH,"
		+ " authorId HASH,"
		+ " timestamp BIGINT NOT NULL,"
		+ " size INT NOT NULL,"
		+ " raw BLOB NOT NULL,"
		+ " sendability INT,"
		+ " contactId INT,"
		+ " PRIMARY KEY (messageId),"
		+ " FOREIGN KEY (groupId) REFERENCES subscriptions (groupId)"
		+ " ON DELETE CASCADE,"
		+ " FOREIGN KEY (contactId) REFERENCES contacts (contactId)"
		+ " ON DELETE CASCADE)";

	private static final String INDEX_MESSAGES_BY_PARENT =
		"CREATE INDEX messagesByParent ON messages (parentId)";

	private static final String INDEX_MESSAGES_BY_AUTHOR =
		"CREATE INDEX messagesByAuthor ON messages (authorId)";

	private static final String INDEX_MESSAGES_BY_BIGINT =
		"CREATE INDEX messagesByTimestamp ON messages (timestamp)";

	private static final String INDEX_MESSAGES_BY_SENDABILITY =
		"CREATE INDEX messagesBySendability ON messages (sendability)";

	private static final String CREATE_VISIBILITIES =
		"CREATE TABLE visibilities"
		+ " (groupId HASH NOT NULL,"
		+ " contactId INT NOT NULL,"
		+ " PRIMARY KEY (groupId, contactId),"
		+ " FOREIGN KEY (groupId) REFERENCES subscriptions (groupId)"
		+ " ON DELETE CASCADE,"
		+ " FOREIGN KEY (contactId) REFERENCES contacts (contactId)"
		+ " ON DELETE CASCADE)";

	private static final String INDEX_VISIBILITIES_BY_GROUP =
		"CREATE INDEX visibilitiesByGroup ON visibilities (groupId)";

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
		+ " groupKey BINARY,"
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

	private static final String CREATE_CONTACT_TRANSPORTS =
		"CREATE TABLE contactTransports"
		+ " (contactId INT NOT NULL,"
		+ " name VARCHAR NOT NULL,"
		+ " key VARCHAR NOT NULL,"
		+ " value VARCHAR NOT NULL,"
		+ " PRIMARY KEY (contactId, name, key),"
		+ " FOREIGN KEY (contactId) REFERENCES contacts (contactId)"
		+ " ON DELETE CASCADE)";

	private static final String CREATE_TRANSPORTS =
		"CREATE TABLE transports"
		+ " (name VARCHAR NOT NULL,"
		+ " key VARCHAR NOT NULL,"
		+ " value VARCHAR NOT NULL,"
		+ " PRIMARY KEY (name, key))";

	private static final String CREATE_TRANSPORT_CONFIG =
		"CREATE TABLE transportConfig"
		+ " (name VARCHAR NOT NULL,"
		+ " key VARCHAR NOT NULL,"
		+ " value VARCHAR NOT NULL,"
		+ " PRIMARY KEY (name, key))";

	private static final String CREATE_CONNECTION_WINDOWS =
		"CREATE TABLE connectionWindows"
		+ " (contactId INT NOT NULL,"
		+ " transportId INT NOT NULL,"
		+ " centre BIGINT NOT NULL,"
		+ " bitmap INT NOT NULL,"
		+ " PRIMARY KEY (contactId, transportId),"
		+ " FOREIGN KEY (contactId) REFERENCES contacts (contactId)"
		+ " ON DELETE CASCADE)";

	private static final String CREATE_SUBSCRIPTION_TIMESTAMPS =
		"CREATE TABLE subscriptionTimestamps"
		+ " (contactId INT NOT NULL,"
		+ " sent BIGINT NOT NULL,"
		+ " received BIGINT NOT NULL,"
		+ " PRIMARY KEY (contactId),"
		+ " FOREIGN KEY (contactId) REFERENCES contacts (contactId)"
		+ " ON DELETE CASCADE)";

	private static final String CREATE_TRANSPORT_TIMESTAMPS =
		"CREATE TABLE transportTimestamps"
		+ " (contactId INT NOT NULL,"
		+ " sent BIGINT NOT NULL,"
		+ " received BIGINT NOT NULL,"
		+ " PRIMARY KEY (contactId),"
		+ " FOREIGN KEY (contactId) REFERENCES contacts (contactId)"
		+ " ON DELETE CASCADE)";

	private static final Logger LOG =
		Logger.getLogger(JdbcDatabase.class.getName());

	// Different database libraries use different names for certain types
	private final String hashType, binaryType;
	private final ConnectionWindowFactory connectionWindowFactory;
	private final GroupFactory groupFactory;
	private final LinkedList<Connection> connections =
		new LinkedList<Connection>(); // Locking: self

	private volatile int openConnections = 0; // Locking: connections
	private volatile boolean closed = false; // Locking: connections

	protected abstract Connection createConnection() throws SQLException;

	JdbcDatabase(ConnectionWindowFactory connectionWindowFactory,
			GroupFactory groupFactory, String hashType, String binaryType) {
		this.connectionWindowFactory = connectionWindowFactory;
		this.groupFactory = groupFactory;
		this.hashType = hashType;
		this.binaryType = binaryType;
	}

	protected void open(boolean resume, File dir, String driverClass)
	throws DbException {
		if(resume) {
			if(!dir.exists()) throw new DbException();
			if(!dir.isDirectory()) throw new DbException();
			if(LOG.isLoggable(Level.FINE))
				LOG.fine("Resuming from " + dir.getPath());
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
			if(resume) {
				if(LOG.isLoggable(Level.FINE))
					LOG.fine(getNumberOfMessages(txn) + " messages");
			}
			else {
				if(LOG.isLoggable(Level.FINE))
					LOG.fine("Creating database tables");
				createTables(txn);
			}
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
			s.executeUpdate(INDEX_MESSAGES_BY_BIGINT);
			s.executeUpdate(INDEX_MESSAGES_BY_SENDABILITY);
			s.executeUpdate(insertTypeNames(CREATE_VISIBILITIES));
			s.executeUpdate(INDEX_VISIBILITIES_BY_GROUP);
			s.executeUpdate(insertTypeNames(CREATE_BATCHES_TO_ACK));
			s.executeUpdate(insertTypeNames(CREATE_CONTACT_SUBSCRIPTIONS));
			s.executeUpdate(insertTypeNames(CREATE_OUTSTANDING_BATCHES));
			s.executeUpdate(insertTypeNames(CREATE_OUTSTANDING_MESSAGES));
			s.executeUpdate(INDEX_OUTSTANDING_MESSAGES_BY_BATCH);
			s.executeUpdate(insertTypeNames(CREATE_RATINGS));
			s.executeUpdate(insertTypeNames(CREATE_STATUSES));
			s.executeUpdate(INDEX_STATUSES_BY_MESSAGE);
			s.executeUpdate(INDEX_STATUSES_BY_CONTACT);
			s.executeUpdate(insertTypeNames(CREATE_CONTACT_TRANSPORTS));
			s.executeUpdate(insertTypeNames(CREATE_TRANSPORTS));
			s.executeUpdate(insertTypeNames(CREATE_TRANSPORT_CONFIG));
			s.executeUpdate(insertTypeNames(CREATE_CONNECTION_WINDOWS));
			s.executeUpdate(insertTypeNames(CREATE_SUBSCRIPTION_TIMESTAMPS));
			s.executeUpdate(insertTypeNames(CREATE_TRANSPORT_TIMESTAMPS));
			s.close();
		} catch(SQLException e) {
			tryToClose(s);
			throw new DbException(e);
		}
	}

	private String insertTypeNames(String s) {
		s = s.replaceAll("HASH", hashType);
		s = s.replaceAll("BINARY", binaryType);
		return s;
	}

	private void tryToClose(Statement s) {
		if(s != null) try {
			s.close();
		} catch(SQLException e) {
			if(LOG.isLoggable(Level.WARNING))
				LOG.warning(e.getMessage());
		}
	}

	private void tryToClose(ResultSet rs) {
		if(rs != null) try {
			rs.close();
		} catch(SQLException e) {
			if(LOG.isLoggable(Level.WARNING))
				LOG.warning(e.getMessage());
		}
	}

	public Connection startTransaction() throws DbException {
		Connection txn = null;
		synchronized(connections) {
			// If the database has been closed, don't return
			while(closed) {
				try {
					connections.wait();
				} catch(InterruptedException ignored) {}
			}
			txn = connections.poll();
		}
		try {
			if(txn == null) {
				// Open a new connection
				txn = createConnection();
				if(txn == null) throw new DbException();
				synchronized(connections) {
					openConnections++;
					if(LOG.isLoggable(Level.FINE))
						LOG.fine(openConnections + " open connections");
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
			if(LOG.isLoggable(Level.WARNING))
				LOG.warning(e.getMessage());
			try {
				txn.close();
			} catch(SQLException e1) {
				if(LOG.isLoggable(Level.WARNING))
					LOG.warning(e1.getMessage());
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
		synchronized(connections) {
			closed = true;
			for(Connection c : connections) c.close();
			openConnections -= connections.size();
			connections.clear();
			while(openConnections > 0) {
				if(LOG.isLoggable(Level.FINE))
					LOG.fine("Waiting for " + openConnections
							+ " open connections");
				try {
					connections.wait();
				} catch(InterruptedException ignored) {}
				for(Connection c : connections) c.close();
				openConnections -= connections.size();
				connections.clear();
			}
		}
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

	public ContactId addContact(Connection txn,
			Map<String, Map<String, String>> transports, byte[] secret)
	throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Get the highest existing contact ID
			String sql = "SELECT contactId FROM contacts"
				+ " ORDER BY contactId DESC LIMIT ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, 1);
			rs = ps.executeQuery();
			int nextId = rs.next() ? rs.getInt(1) + 1 : 1;
			ContactId c = new ContactId(nextId);
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			// Create a new contact row
			sql = "INSERT INTO contacts (contactId, secret) VALUES (?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setBytes(2, secret);
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			// Store the contact's transport properties
			sql = "INSERT INTO contactTransports"
				+ " (contactId, name, key, value)"
				+ " VALUES (?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			int batchSize = 0;
			for(Entry<String, Map<String, String>> e : transports.entrySet()) {
				ps.setString(2, e.getKey());
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
			// Initialise the subscription timestamps
			sql = "INSERT INTO subscriptionTimestamps"
				+ " (contactId, sent, received)"
				+ " VALUES (?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setLong(2, 0L);
			ps.setLong(3, 0L);
			affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			// Initialise the transport timestamps
			sql = "INSERT INTO transportTimestamps"
				+ " (contactId, sent, received)"
				+ " VALUES (?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setLong(2, 0L);
			ps.setLong(3, 0L);
			affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
			return c;
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
			String sql = "INSERT INTO messages"
				+ " (messageId, parentId, groupId, authorId, timestamp, size,"
				+ " raw, sendability)"
				+ " VALUES (?, ?, ?, ?, ?, ?, ?, ZERO())";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getId().getBytes());
			if(m.getParent() == null) ps.setNull(2, Types.BINARY);
			else ps.setBytes(2, m.getParent().getBytes());
			ps.setBytes(3, m.getGroup().getBytes());
			if(m.getAuthor() == null) ps.setNull(4, Types.BINARY);
			else ps.setBytes(4, m.getAuthor().getBytes());
			ps.setLong(5, m.getTimestamp());
			ps.setInt(6, m.getSize());
			byte[] raw = m.getBytes();
			ps.setBinaryStream(7, new ByteArrayInputStream(raw), raw.length);
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
			ps.setLong(3, System.currentTimeMillis());
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
				+ " (messageId, parentId, timestamp, size, raw, contactId)"
				+ " VALUES (?, ?, ?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getId().getBytes());
			if(m.getParent() == null) ps.setNull(2, Types.BINARY);
			else ps.setBytes(2, m.getParent().getBytes());
			ps.setLong(3, m.getTimestamp());
			ps.setInt(4, m.getSize());
			byte[] raw = m.getBytes();
			ps.setBinaryStream(5, new ByteArrayInputStream(raw), raw.length);
			ps.setInt(6, c.getInt());
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
			ps.setLong(4, System.currentTimeMillis());
			int affected = ps.executeUpdate();
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
		boolean found = false;
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT start FROM subscriptions WHERE groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
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

	public boolean containsVisibleSubscription(Connection txn, GroupId g,
			ContactId c, long time) throws DbException {
		boolean found = false;
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT start FROM subscriptions JOIN visibilities"
				+ " ON subscriptions.groupId = visibilities.groupId"
				+ " WHERE subscriptions.groupId = ? AND contactId = ?";
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

	public Collection<BatchId> getBatchesToAck(Connection txn, ContactId c)
	throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT batchId FROM batchesToAck"
				+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			Collection<BatchId> ids = new ArrayList<BatchId>();
			while(rs.next()) ids.add(new BatchId(rs.getBytes(1)));
			rs.close();
			ps.close();
			return ids;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public ConnectionWindow getConnectionWindow(Connection txn, ContactId c,
			int transportId) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT centre, bitmap FROM connectionWindows"
				+ " WHERE contactId = ? AND transportId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setInt(2, transportId);
			rs = ps.executeQuery();
			long centre = 0L;
			int bitmap = 0;
			if(rs.next()) {
				centre = rs.getLong(1);
				bitmap = rs.getInt(2);
				if(rs.next()) throw new DbStateException();
			}
			rs.close();
			ps.close();
			return connectionWindowFactory.createConnectionWindow(centre,
					bitmap);
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
			Collection<ContactId> ids = new ArrayList<ContactId>();
			while(rs.next()) ids.add(new ContactId(rs.getInt(1)));
			rs.close();
			ps.close();
			return ids;
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

	public GroupId getGroup(Connection txn, MessageId m) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT groupId FROM messages WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			rs = ps.executeQuery();
			if(!rs.next()) throw new DbStateException();
			byte[] group = rs.getBytes(1);
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return new GroupId(group);
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
			Collection<BatchId> ids = new ArrayList<BatchId>();
			while(rs.next()) ids.add(new BatchId(rs.getBytes(1)));
			rs.close();
			ps.close();
			return ids;
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
			String sql = "SELECT size, raw FROM messages WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			rs = ps.executeQuery();
			if(!rs.next()) throw new DbStateException();
			int size = rs.getInt(1);
			Blob b = rs.getBlob(2);
			byte[] raw = b.getBytes(1, size);
			if(raw.length != size) throw new DbStateException();
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

	public byte[] getMessageIfSendable(Connection txn, ContactId c, MessageId m)
	throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Do we have a sendable private message with the given ID?
			String sql = "SELECT size, raw FROM messages"
				+ " JOIN statuses ON messages.messageId = statuses.messageId"
				+ " WHERE messages.messageId = ? AND messages.contactId = ?"
				+ " AND status = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setInt(2, c.getInt());
			ps.setShort(3, (short) Status.NEW.ordinal());
			rs = ps.executeQuery();
			byte[] raw = null;
			if(rs.next()) {
				int size = rs.getInt(1);
				Blob b = rs.getBlob(2);
				raw = b.getBytes(1, size);
				if(raw.length != size) throw new DbStateException();
			}
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			if(raw != null) return raw;
			// Do we have a sendable group message with the given ID?
			sql = "SELECT size, raw FROM messages"
				+ " JOIN contactSubscriptions"
				+ " ON messages.groupId = contactSubscriptions.groupId"
				+ " JOIN visibilities"
				+ " ON messages.groupId = visibilities.groupId"
				+ " AND contactSubscriptions.contactId = visibilities.contactId"
				+ " JOIN statuses"
				+ " ON messages.messageId = statuses.messageId"
				+ " AND contactSubscriptions.contactId = statuses.contactId"
				+ " WHERE messages.messageId = ?"
				+ " AND contactSubscriptions.contactId = ?"
				+ " AND timestamp >= start"
				+ " AND status = ?"
				+ " AND sendability > ZERO()";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setInt(2, c.getInt());
			ps.setShort(3, (short) Status.NEW.ordinal());
			rs = ps.executeQuery();
			if(rs.next()) {
				int size = rs.getInt(1);
				Blob b = rs.getBlob(2);
				raw = b.getBytes(1, size);
				if(raw.length != size) throw new DbStateException();
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
			Collection<MessageId> ids = new ArrayList<MessageId>();
			while(rs.next()) ids.add(new MessageId(rs.getBytes(1)));
			rs.close();
			ps.close();
			return ids;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	private int getNumberOfMessages(Connection txn) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT COUNT(messageId) FROM messages";
			ps = txn.prepareStatement(sql);
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
			byte[] group = rs.getBytes(1);
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			sql = "SELECT COUNT(messageId) FROM messages"
				+ " WHERE parentId = ? AND groupId = ?"
				+ " AND sendability > ZERO()";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setBytes(2, group);
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
			String sql = "SELECT size, messageId FROM messages"
				+ " ORDER BY timestamp";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			Collection<MessageId> ids = new ArrayList<MessageId>();
			int total = 0;
			while(rs.next()) {
				int size = rs.getInt(1);
				if(total + size > capacity) break;
				ids.add(new MessageId(rs.getBytes(2)));
				total += size;
			}
			rs.close();
			ps.close();
			if(LOG.isLoggable(Level.FINE))
				LOG.fine(ids.size() + " old messages, " + total + " bytes");
			return ids;
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
			String sql = "SELECT parentId FROM messages WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			rs = ps.executeQuery();
			if(!rs.next()) throw new DbStateException();
			byte[] parent = rs.getBytes(1);
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return parent == null ? null : new MessageId(parent);
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

	public byte[] getSharedSecret(Connection txn, ContactId c)
	throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT secret FROM contacts WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			if(!rs.next()) throw new DbStateException();
			byte[] secret = rs.getBytes(1);
			if(rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return secret;
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
			ContactId c, int capacity) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Do we have any sendable private messages?
			String sql = "SELECT size, messages.messageId FROM messages"
				+ " JOIN statuses ON messages.messageId = statuses.messageId"
				+ " WHERE messages.contactId = ? AND status = ?"
				+ " ORDER BY timestamp";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setShort(2, (short) Status.NEW.ordinal());
			rs = ps.executeQuery();
			Collection<MessageId> ids = new ArrayList<MessageId>();
			int total = 0;
			while(rs.next()) {
				int size = rs.getInt(1);
				if(total + size > capacity) break;
				ids.add(new MessageId(rs.getBytes(2)));
				total += size;
			}
			rs.close();
			ps.close();
			if(LOG.isLoggable(Level.FINE))
				LOG.fine(ids.size() + " sendable private messages, " +
						total + "/" + capacity + " bytes");
			if(total == capacity) return ids;
			// Do we have any sendable group messages?
			sql = "SELECT size, messages.messageId FROM messages"
				+ " JOIN contactSubscriptions"
				+ " ON messages.groupId = contactSubscriptions.groupId"
				+ " JOIN visibilities"
				+ " ON messages.groupId = visibilities.groupId"
				+ " AND contactSubscriptions.contactId = visibilities.contactId"
				+ " JOIN statuses"
				+ " ON messages.messageId = statuses.messageId"
				+ " AND contactSubscriptions.contactId = statuses.contactId"
				+ " WHERE contactSubscriptions.contactId = ?"
				+ " AND timestamp >= start"
				+ " AND status = ?"
				+ " AND sendability > ZERO()"
				+ " ORDER BY timestamp";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setShort(2, (short) Status.NEW.ordinal());
			rs = ps.executeQuery();
			while(rs.next()) {
				int size = rs.getInt(1);
				if(total + size > capacity) break;
				ids.add(new MessageId(rs.getBytes(2)));
				total += size;
			}
			rs.close();
			ps.close();
			if(LOG.isLoggable(Level.FINE))
				LOG.fine(ids.size() + " sendable private and group messages, " +
						total + "/" + capacity + " bytes");
			return ids;
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
			Collection<Group> subs = new ArrayList<Group>();
			while(rs.next()) {
				GroupId id = new GroupId(rs.getBytes(1));
				String name = rs.getString(2);
				byte[] publicKey = rs.getBytes(3);
				subs.add(groupFactory.createGroup(id, name, publicKey));
			}
			rs.close();
			ps.close();
			return subs;
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
			Collection<Group> subs = new ArrayList<Group>();
			while(rs.next()) {
				GroupId id = new GroupId(rs.getBytes(1));
				String name = rs.getString(2);
				byte[] publicKey = rs.getBytes(3);
				subs.add(groupFactory.createGroup(id, name, publicKey));
			}
			rs.close();
			ps.close();
			return subs;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public Map<String, String> getTransportConfig(Connection txn,
			String name) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT key, value FROM transportConfig"
				+ " WHERE name = ?";
			ps = txn.prepareStatement(sql);
			ps.setString(1, name);
			rs = ps.executeQuery();
			Map<String, String> config = new TreeMap<String, String>();
			while(rs.next()) config.put(rs.getString(1), rs.getString(2));
			rs.close();
			ps.close();
			return config;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public Map<String, Map<String, String>> getTransports(Connection txn)
	throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT name, key, value FROM transports"
				+ " ORDER BY name";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			Map<String, Map<String, String>> transports =
				new TreeMap<String, Map<String, String>>();
			Map<String, String> properties = null;
			String lastName = null;
			while(rs.next()) {
				String name = rs.getString(1);
				if(!name.equals(lastName)) {
					properties = new TreeMap<String, String>();
					transports.put(name, properties);
				}
				properties.put(rs.getString(2), rs.getString(3));
			}
			rs.close();
			ps.close();
			return transports;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public Map<String, Map<String, String>> getTransports(Connection txn,
			ContactId c) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT name, key, value FROM contactTransports"
				+ " WHERE contactId = ?"
				+ " ORDER BY name";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			Map<String, Map<String, String>> transports =
				new TreeMap<String, Map<String, String>>();
			Map<String, String> properties = null;
			String lastName = null;
			while(rs.next()) {
				String name = rs.getString(1);
				if(!name.equals(lastName)) {
					properties = new TreeMap<String, String>();
					transports.put(name, properties);
				}
				properties.put(rs.getString(2), rs.getString(3));
			}
			rs.close();
			ps.close();
			return transports;
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
			Collection<ContactId> visible = new ArrayList<ContactId>();
			while(rs.next()) visible.add(new ContactId(rs.getInt(1)));
			rs.close();
			ps.close();
			return visible;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public Map<Group, Long> getVisibleSubscriptions(Connection txn,
			ContactId c) throws DbException {
		long expiry = getApproximateExpiryTime(txn);
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql =
				"SELECT subscriptions.groupId, groupName, groupKey, start"
				+ " FROM subscriptions JOIN visibilities"
				+ " ON subscriptions.groupId = visibilities.groupId"
				+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			Map<Group, Long> subs = new HashMap<Group, Long>();
			while(rs.next()) {
				GroupId id = new GroupId(rs.getBytes(1));
				String name = rs.getString(2);
				byte[] publicKey = rs.getBytes(3);
				Group g = groupFactory.createGroup(id, name, publicKey);
				long start = Math.max(rs.getLong(4), expiry);
				subs.put(g, start);
			}
			rs.close();
			ps.close();
			return subs;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	private long getApproximateExpiryTime(Connection txn) throws DbException {
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
				timestamp -= timestamp % DatabaseComponent.EXPIRY_MODULUS;
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

	public boolean hasSendableMessages(Connection txn, ContactId c)
	throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT messages.messageId FROM messages"
				+ " JOIN contactSubscriptions"
				+ " ON messages.groupId = contactSubscriptions.groupId"
				+ " JOIN visibilities"
				+ " ON messages.groupId = visibilities.groupId"
				+ " JOIN statuses ON messages.messageId = statuses.messageId"
				+ " WHERE contactSubscriptions.contactId = ?"
				+ " AND visibilities.contactId = ?"
				+ " AND statuses.contactId = ?"
				+ " AND timestamp >= start"
				+ " AND status = ? AND sendability > ZERO()"
				+ " LIMIT ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setInt(2, c.getInt());
			ps.setInt(3, c.getInt());
			ps.setShort(4, (short) Status.NEW.ordinal());
			ps.setInt(5, 1);
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
		PreparedStatement ps = null;
		try {
			String sql = "DELETE FROM subscriptions WHERE groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void setConnectionWindow(Connection txn, ContactId c,
			int transportId, ConnectionWindow w) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT NULL FROM connectionWindows"
				+ " WHERE contactId = ? AND transportId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setInt(2, transportId);
			rs = ps.executeQuery();
			if(rs.next()) {
				if(rs.next()) throw new DbStateException();
				rs.close();
				ps.close();
				sql = "UPDATE connectionWindows SET centre = ?, bitmap = ?"
					+ " WHERE contactId = ? AND transportId = ?";
				ps = txn.prepareStatement(sql);
				int affected = ps.executeUpdate();
				if(affected != 1) throw new DbStateException();
			} else {
				rs.close();
				ps.close();
				sql = "INSERT INTO connectionWindows"
					+ " (contactId, transportId, centre, bitmap)"
					+ " VALUES(?, ?, ?, ?)";
				ps = txn.prepareStatement(sql);
				ps.setInt(1, c.getInt());
				ps.setInt(2, transportId);
				ps.setLong(3, w.getCentre());
				ps.setInt(4, w.getBitmap());
				int affected = ps.executeUpdate();
				if(affected != 1) throw new DbStateException();
			}
			ps.close();
		} catch(SQLException e) {
			tryToClose(rs);
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
				old = Rating.values()[rs.getByte(1)];
				if(rs.next()) throw new DbStateException();
				rs.close();
				ps.close();
				sql = "UPDATE ratings SET rating = ? WHERE authorId = ?";
				ps = txn.prepareStatement(sql);
				ps.setShort(1, (short) r.ordinal());
				ps.setBytes(2, a.getBytes());
				int affected = ps.executeUpdate();
				if(affected != 1) throw new DbStateException();
				ps.close();
			} else {
				rs.close();
				ps.close();
				old = Rating.UNRATED;
				sql = "INSERT INTO ratings (authorId, rating) VALUES (?, ?)";
				ps = txn.prepareStatement(sql);
				ps.setBytes(1, a.getBytes());
				ps.setShort(2, (short) r.ordinal());
				int affected = ps.executeUpdate();
				if(affected != 1) throw new DbStateException();
				ps.close();
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
			String sql = "SELECT NULL FROM messages"
				+ " JOIN contactSubscriptions"
				+ " ON messages.groupId = contactSubscriptions.groupId"
				+ " JOIN visibilities"
				+ " ON messages.groupId = visibilities.groupId"
				+ " WHERE messageId = ?"
				+ " AND contactSubscriptions.contactId = ?"
				+ " AND visibilities.contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setInt(2, c.getInt());
			ps.setInt(3, c.getInt());
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
			Map<Group, Long> subs, long timestamp) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Return if the timestamp isn't fresh
			String sql = "SELECT received FROM subscriptionTimestamps"
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
			// Delete any existing subscriptions
			sql = "DELETE FROM contactSubscriptions WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.executeUpdate();
			ps.close();
			// Store the new subscriptions
			sql = "INSERT INTO contactSubscriptions"
				+ " (contactId, groupId, groupName, groupKey, start)"
				+ " VALUES (?, ?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			for(Entry<Group, Long> e : subs.entrySet()) {
				Group g = e.getKey();
				ps.setBytes(2, g.getId().getBytes());
				ps.setString(3, g.getName());
				ps.setBytes(4, g.getPublicKey());
				ps.setLong(5, e.getValue());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if(batchAffected.length != subs.size())
				throw new DbStateException();
			for(int i = 0; i < batchAffected.length; i++) {
				if(batchAffected[i] != 1) throw new DbStateException();
			}
			ps.close();
			// Update the timestamp
			sql = "UPDATE subscriptionTimestamps SET received = ?"
				+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setLong(1, timestamp);
			ps.setInt(2, c.getInt());
			int affected = ps.executeUpdate();
			if(affected != 1) throw new DbStateException();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void setSubscriptionTimestamp(Connection txn, ContactId c,
			long timestamp) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "UPDATE subscriptionTimestamps SET sent = ?"
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

	public void setTransportConfig(Connection txn, String name,
			Map<String, String> config) throws DbException {
		setTransportDetails(txn, name, config, "transportConfig");
	}

	private void setTransportDetails(Connection txn, String name,
			Map<String, String> details, String table) throws DbException {
		PreparedStatement ps = null;
		try {
			// Delete any existing details for the named transport
			String sql = "DELETE FROM " + table + " WHERE name = ?";
			ps = txn.prepareStatement(sql);
			ps.setString(1, name);
			ps.executeUpdate();
			ps.close();
			// Store the new details
			sql = "INSERT INTO " + table + " (name, key, value)"
			+ " VALUES (?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setString(1, name);
			for(Entry<String, String> e : details.entrySet()) {
				ps.setString(2, e.getKey());
				ps.setString(3, e.getValue());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if(batchAffected.length != details.size())
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

	public void setTransportProperties(Connection txn, String name,
			Map<String, String> properties) throws DbException {
		setTransportDetails(txn, name, properties, "transports");
	}

	public void setTransports(Connection txn, ContactId c,
			Map<String, Map<String, String>> transports, long timestamp)
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
			// Delete any existing transports
			sql = "DELETE FROM contactTransports WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.executeUpdate();
			ps.close();
			// Store the new transports
			sql = "INSERT INTO contactTransports (contactId, name, key, value)"
				+ " VALUES (?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			int batchSize = 0;
			for(Entry<String, Map<String, String>> e : transports.entrySet()) {
				ps.setString(2, e.getKey());
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
			tryToClose(ps);
			throw new DbException(e);
		}
	}

	public void setTransportTimestamp(Connection txn, ContactId c,
			long timestamp) throws DbException {
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

	public void setVisibility(Connection txn, GroupId g,
			Collection<ContactId> visible) throws DbException {
		PreparedStatement ps = null;
		try {
			// Delete any existing visibilities
			String sql = "DELETE FROM visibilities WHERE groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			ps.executeUpdate();
			ps.close();
			if(visible.isEmpty()) return;
			// Store the new visibilities
			sql = "INSERT INTO visibilities (groupId, contactId)"
				+ " VALUES (?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			for(ContactId c : visible) {
				ps.setInt(2, c.getInt());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if(batchAffected.length != visible.size())
				throw new DbStateException();
			for(int i = 0; i < batchAffected.length; i++) {
				if(batchAffected[i] != 1) throw new DbStateException();
			}
		} catch(SQLException e) {
			tryToClose(ps);
			throw new DbException(e);
		}
	}
}
