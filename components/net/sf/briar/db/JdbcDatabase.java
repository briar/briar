package net.sf.briar.db;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.Rating;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.Status;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageFactory;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.util.FileUtils;

/**
 * A generic database implementation that can be used with any JDBC-compatible
 * database library. (Tested with H2, Derby and HSQLDB.)
 */
abstract class JdbcDatabase implements Database<Connection> {

	private static final String CREATE_LOCAL_SUBSCRIPTIONS =
		"CREATE TABLE localSubscriptions"
		+ " (groupId XXXX NOT NULL,"
		+ " PRIMARY KEY (groupId))";

	private static final String CREATE_MESSAGES =
		"CREATE TABLE messages"
		+ " (messageId XXXX NOT NULL,"
		+ " parentId XXXX NOT NULL,"
		+ " groupId XXXX NOT NULL,"
		+ " authorId XXXX NOT NULL,"
		+ " timestamp BIGINT NOT NULL,"
		+ " size INT NOT NULL,"
		+ " raw BLOB NOT NULL,"
		+ " sendability INT NOT NULL,"
		+ " PRIMARY KEY (messageId),"
		+ " FOREIGN KEY (groupId) REFERENCES localSubscriptions (groupId)"
		+ " ON DELETE CASCADE)";

	private static final String INDEX_MESSAGES_BY_PARENT =
		"CREATE INDEX messagesByParent ON messages (parentId)";

	private static final String INDEX_MESSAGES_BY_AUTHOR =
		"CREATE INDEX messagesByAuthor ON messages (authorId)";

	private static final String INDEX_MESSAGES_BY_TIMESTAMP =
		"CREATE INDEX messagesByTimestamp ON messages (timestamp)";

	private static final String INDEX_MESSAGES_BY_SENDABILITY =
		"CREATE INDEX messagesBySendability ON messages (sendability)";

	private static final String CREATE_CONTACTS =
		"CREATE TABLE contacts"
		+ " (contactId INT NOT NULL,"
		+ " PRIMARY KEY (contactId))";

	private static final String CREATE_BATCHES_TO_ACK =
		"CREATE TABLE batchesToAck"
		+ " (batchId XXXX NOT NULL,"
		+ " contactId INT NOT NULL,"
		+ " PRIMARY KEY (batchId),"
		+ " FOREIGN KEY (contactId) REFERENCES contacts (contactId)"
		+ " ON DELETE CASCADE)";

	private static final String CREATE_CONTACT_SUBSCRIPTIONS =
		"CREATE TABLE contactSubscriptions"
		+ " (contactId INT NOT NULL,"
		+ " groupId XXXX NOT NULL,"
		+ " PRIMARY KEY (contactId, groupId),"
		+ " FOREIGN KEY (contactId) REFERENCES contacts (contactId)"
		+ " ON DELETE CASCADE)";

	private static final String CREATE_OUTSTANDING_BATCHES =
		"CREATE TABLE outstandingBatches"
		+ " (batchId XXXX NOT NULL,"
		+ " contactId INT NOT NULL,"
		+ " timestamp YYYY NOT NULL,"
		+ " passover INT NOT NULL,"
		+ " PRIMARY KEY (batchId),"
		+ " FOREIGN KEY (contactId) REFERENCES contacts (contactId)"
		+ " ON DELETE CASCADE)";

	private static final String CREATE_OUTSTANDING_MESSAGES =
		"CREATE TABLE outstandingMessages"
		+ " (batchId XXXX NOT NULL,"
		+ " contactId INT NOT NULL,"
		+ " messageId XXXX NOT NULL,"
		+ " PRIMARY KEY (batchId, messageId),"
		+ " FOREIGN KEY (batchId) REFERENCES outstandingBatches (batchId)"
		+ " ON DELETE CASCADE,"
		+ " FOREIGN KEY (contactId) REFERENCES contacts (contactId)"
		+ " ON DELETE CASCADE,"
		+ " FOREIGN KEY (messageId) REFERENCES messages (messageId)"
		+ " ON DELETE CASCADE)";

	private static final String INDEX_OUTSTANDING_MESSAGES_BY_BATCH =
		"CREATE INDEX outstandingMessagesByBatch"
		+ " ON outstandingMessages (batchId)";

	private static final String CREATE_RATINGS =
		"CREATE TABLE ratings"
		+ " (authorId XXXX NOT NULL,"
		+ " rating SMALLINT NOT NULL,"
		+ " PRIMARY KEY (authorId))";

	private static final String CREATE_STATUSES =
		"CREATE TABLE statuses"
		+ " (messageId XXXX NOT NULL,"
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
		+ " key VARCHAR NOT NULL,"
		+ " value VARCHAR NOT NULL,"
		+ " PRIMARY KEY (contactId, key),"
		+ " FOREIGN KEY (contactId) REFERENCES contacts (contactId)"
		+ " ON DELETE CASCADE)";

	private static final String CREATE_LOCAL_TRANSPORTS =
		"CREATE TABLE localTransports"
		+ " (key VARCHAR NOT NULL,"
		+ " value VARCHAR NOT NULL,"
		+ " PRIMARY KEY (key))";

	private static final Logger LOG =
		Logger.getLogger(JdbcDatabase.class.getName());

	private final MessageFactory messageFactory;
	// Different database libraries use different names for certain types
	private final String hashType, bigIntType;
	private final LinkedList<Connection> connections =
		new LinkedList<Connection>(); // Locking: self

	private volatile int openConnections = 0; // Locking: connections
	private volatile boolean closed = false; // Locking: connections

	protected abstract Connection createConnection() throws SQLException;

	JdbcDatabase(MessageFactory messageFactory, String hashType,
			String bigIntType) {
		this.messageFactory = messageFactory;
		this.hashType = hashType;
		this.bigIntType = bigIntType;
	}

	protected void open(boolean resume, File dir, String driverClass)
	throws DbException {
		if(resume) {
			assert dir.exists();
			assert dir.isDirectory();
			if(LOG.isLoggable(Level.FINE))
				LOG.fine("Resuming from " + dir.getPath());
		} else {
			if(dir.exists()) FileUtils.delete(dir);
		}
		try {
			Class.forName(driverClass);
		} catch(ClassNotFoundException e) {
			throw new DbException(e);
		}
		Connection txn = startTransaction();
		try {
			// If not resuming, create the tables
			if(resume) {
				if(LOG.isLoggable(Level.FINE))
					LOG.fine(getNumberOfMessages(txn) + " messages");
			}
			else createTables(txn);
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
			if(LOG.isLoggable(Level.FINE))
				LOG.fine("Creating localSubscriptions table");
			s.executeUpdate(insertTypeNames(CREATE_LOCAL_SUBSCRIPTIONS));
			if(LOG.isLoggable(Level.FINE))
				LOG.fine("Creating messages table");
			s.executeUpdate(insertTypeNames(CREATE_MESSAGES));
			s.executeUpdate(INDEX_MESSAGES_BY_PARENT);
			s.executeUpdate(INDEX_MESSAGES_BY_AUTHOR);
			s.executeUpdate(INDEX_MESSAGES_BY_TIMESTAMP);
			s.executeUpdate(INDEX_MESSAGES_BY_SENDABILITY);
			if(LOG.isLoggable(Level.FINE))
				LOG.fine("Creating contacts table");
			s.executeUpdate(insertTypeNames(CREATE_CONTACTS));
			if(LOG.isLoggable(Level.FINE))
				LOG.fine("Creating batchesToAck table");
			s.executeUpdate(insertTypeNames(CREATE_BATCHES_TO_ACK));
			if(LOG.isLoggable(Level.FINE))
				LOG.fine("Creating contactSubscriptions table");
			s.executeUpdate(insertTypeNames(CREATE_CONTACT_SUBSCRIPTIONS));
			if(LOG.isLoggable(Level.FINE))
				LOG.fine("Creating outstandingBatches table");
			s.executeUpdate(insertTypeNames(CREATE_OUTSTANDING_BATCHES));
			if(LOG.isLoggable(Level.FINE))
				LOG.fine("Creating outstandingMessages table");
			s.executeUpdate(insertTypeNames(CREATE_OUTSTANDING_MESSAGES));
			s.executeUpdate(INDEX_OUTSTANDING_MESSAGES_BY_BATCH);
			if(LOG.isLoggable(Level.FINE))
				LOG.fine("Creating ratings table");
			s.executeUpdate(insertTypeNames(CREATE_RATINGS));
			if(LOG.isLoggable(Level.FINE))
				LOG.fine("Creating statuses table");
			s.executeUpdate(insertTypeNames(CREATE_STATUSES));
			s.executeUpdate(INDEX_STATUSES_BY_MESSAGE);
			s.executeUpdate(INDEX_STATUSES_BY_CONTACT);
			if(LOG.isLoggable(Level.FINE))
				LOG.fine("Creating contact transports table");
			s.executeUpdate(insertTypeNames(CREATE_CONTACT_TRANSPORTS));
			if(LOG.isLoggable(Level.FINE))
				LOG.fine("Creating local transports table");
			s.executeUpdate(insertTypeNames(CREATE_LOCAL_TRANSPORTS));
			s.close();
		} catch(SQLException e) {
			tryToClose(s);
			tryToClose(txn);
			throw new DbException(e);
		}
	}

	private String insertTypeNames(String s) {
		return s.replaceAll("XXXX", hashType).replaceAll("YYYY", bigIntType);
	}

	private void tryToClose(Connection c) {
		if(c != null) try {
			c.close();
		} catch(SQLException ignored) {}
	}

	private void tryToClose(Statement s) {
		if(s != null) try {
			s.close();
		} catch(SQLException ignored) {}
	}

	private void tryToClose(ResultSet rs) {
		if(rs != null) try {
			rs.close();
		} catch(SQLException ignored) {}
	}

	public Connection startTransaction() throws DbException {
		Connection txn = null;
		try {
			synchronized(connections) {
				// If the database has been closed, don't return
				while(closed) {
					try {
						connections.wait();
					} catch(InterruptedException ignored) {}
				}
				txn = connections.poll();
			}
			if(txn == null) {
				txn = createConnection();
				assert txn != null;
				synchronized(connections) {
					openConnections++;
					if(LOG.isLoggable(Level.FINE))
						LOG.fine(openConnections + " open connections");
				}
			}
			txn.setAutoCommit(false);
			return txn;
		} catch(SQLException e) {
			tryToClose(txn);
			throw new DbException(e);
		}
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
			tryToClose(txn);
		}
	}

	public void commitTransaction(Connection txn) throws DbException {
		try {
			txn.commit();
			txn.setAutoCommit(true);
			synchronized(connections) {
				connections.add(txn);
				connections.notifyAll();
			}
		} catch(SQLException e) {
			tryToClose(txn);
			throw new DbException(e);
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
		try {
			String sql = "INSERT INTO batchesToAck (batchId, contactId)"
				+ " VALUES (?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, b.getBytes());
			ps.setInt(2, c.getInt());
			int rowsAffected = ps.executeUpdate();
			assert rowsAffected == 1;
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			tryToClose(txn);
			throw new DbException(e);
		}
	}

	public ContactId addContact(Connection txn, Map<String, String> transports)
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
			boolean more = rs.next();
			assert !more;
			rs.close();
			ps.close();
			// Create a new contact row
			sql = "INSERT INTO contacts (contactId) VALUES (?)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			int rowsAffected = ps.executeUpdate();
			assert rowsAffected == 1;
			ps.close();
			// Store the contact's transport details
			if(transports != null) {
				sql = "INSERT INTO contactTransports (contactId, key, value)"
					+ " VALUES (?, ?, ?)";
				ps = txn.prepareStatement(sql);
				ps.setInt(1, c.getInt());
				for(Entry<String, String> e : transports.entrySet()) {
					ps.setString(2, e.getKey());
					ps.setString(3, e.getValue());
					ps.addBatch();
				}
				int[] rowsAffectedArray = ps.executeBatch();
				assert rowsAffectedArray.length == transports.size();
				for(int i = 0; i < rowsAffectedArray.length; i++) {
					assert rowsAffectedArray[i] == 1;
				}
				ps.close();
			}
			return c;
		} catch(SQLException e) {
			tryToClose(ps);
			tryToClose(txn);
			throw new DbException(e);
		}
	}

	public boolean addMessage(Connection txn, Message m) throws DbException {
		if(containsMessage(txn, m.getId())) return false;
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO messages"
				+ " (messageId, parentId, groupId, authorId, timestamp, size,"
				+ " raw, sendability)"
				+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getId().getBytes());
			ps.setBytes(2, m.getParent().getBytes());
			ps.setBytes(3, m.getGroup().getBytes());
			ps.setBytes(4, m.getAuthor().getBytes());
			ps.setLong(5, m.getTimestamp());
			ps.setInt(6, m.getSize());
			ps.setBlob(7, new ByteArrayInputStream(m.getBytes()));
			ps.setInt(8, 0);
			int rowsAffected = ps.executeUpdate();
			assert rowsAffected == 1;
			ps.close();
			return true;
		} catch(SQLException e) {
			tryToClose(ps);
			tryToClose(txn);
			throw new DbException(e);
		}
	}

	public void addOutstandingBatch(Connection txn, ContactId c, BatchId b,
			Set<MessageId> sent) throws DbException {
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
			int rowsAffected = ps.executeUpdate();
			assert rowsAffected == 1;
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
			int[] rowsAffectedArray = ps.executeBatch();
			assert rowsAffectedArray.length == sent.size();
			for(int i = 0; i < rowsAffectedArray.length; i++) {
				assert rowsAffectedArray[i] == 1;
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
			rowsAffectedArray = ps.executeBatch();
			assert rowsAffectedArray.length == sent.size();
			for(int i = 0; i < rowsAffectedArray.length; i++) {
				assert rowsAffectedArray[i] <= 1;
			}
			ps.close();
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			tryToClose(txn);
			throw new DbException(e);
		}
	}

	public void addSubscription(Connection txn, GroupId g) throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO localSubscriptions (groupId) VALUES (?)";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			int rowsAffected = ps.executeUpdate();
			assert rowsAffected == 1;
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			tryToClose(txn);
			throw new DbException(e);
		}
	}

	public void addSubscription(Connection txn, ContactId c, GroupId g)
	throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO contactSubscriptions"
				+ " (contactId, groupId)"
				+ " VALUES (?, ?)";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setBytes(2, g.getBytes());
			int rowsAffected = ps.executeUpdate();
			assert rowsAffected == 1;
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			tryToClose(txn);
			throw new DbException(e);
		}
	}

	public void clearSubscriptions(Connection txn, ContactId c)
	throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "DELETE FROM contactSubscriptions"
				+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.executeUpdate();
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			tryToClose(txn);
			throw new DbException(e);
		}
	}

	public boolean containsContact(Connection txn, ContactId c)
	throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT COUNT(contactId) FROM contacts"
				+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			boolean found = rs.next();
			assert found;
			int count = rs.getInt(1);
			assert count <= 1;
			boolean more = rs.next();
			assert !more;
			rs.close();
			ps.close();
			return count > 0;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			tryToClose(txn);
			throw new DbException(e);
		}
	}

	public boolean containsMessage(Connection txn, MessageId m)
	throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT COUNT(messageId) FROM messages"
				+ " WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			rs = ps.executeQuery();
			boolean found = rs.next();
			assert found;
			int count = rs.getInt(1);
			assert count <= 1;
			boolean more = rs.next();
			assert !more;
			rs.close();
			ps.close();
			return count > 0;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			tryToClose(txn);
			throw new DbException(e);
		}
	}

	public boolean containsSubscription(Connection txn, GroupId g)
	throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT COUNT(groupId) FROM localSubscriptions"
				+ " WHERE groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			rs = ps.executeQuery();
			boolean found = rs.next();
			assert found;
			int count = rs.getInt(1);
			assert count <= 1;
			boolean more = rs.next();
			assert !more;
			rs.close();
			ps.close();
			return count > 0;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			tryToClose(txn);
			throw new DbException(e);
		}
	}

	public Set<ContactId> getContacts(Connection txn) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT contactId FROM contacts";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			Set<ContactId> ids = new HashSet<ContactId>();
			while(rs.next()) ids.add(new ContactId(rs.getInt(1)));
			rs.close();
			ps.close();
			return ids;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			tryToClose(txn);
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
			boolean found = rs.next();
			assert found;
			byte[] group = rs.getBytes(1);
			boolean more = rs.next();
			assert !more;
			rs.close();
			ps.close();
			return new GroupId(group);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			tryToClose(txn);
			throw new DbException(e);
		}
	}

	public Set<BatchId> getLostBatches(Connection txn, ContactId c)
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
			Set<BatchId> ids = new HashSet<BatchId>();
			while(rs.next()) ids.add(new BatchId(rs.getBytes(1)));
			rs.close();
			ps.close();
			return ids;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			tryToClose(txn);
			throw new DbException(e);
		}
	}

	public Message getMessage(Connection txn, MessageId m) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql =
				"SELECT parentId, groupId, authorId, timestamp, size, raw"
				+ " FROM messages WHERE messageId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			rs = ps.executeQuery();
			boolean found = rs.next();
			assert found;
			MessageId parent = new MessageId(rs.getBytes(1));
			GroupId group = new GroupId(rs.getBytes(2));
			AuthorId author = new AuthorId(rs.getBytes(3));
			long timestamp = rs.getLong(4);
			int size = rs.getInt(5);
			Blob b = rs.getBlob(6);
			byte[] raw = b.getBytes(1, size);
			assert raw.length == size;
			boolean more = rs.next();
			assert !more;
			rs.close();
			ps.close();
			return messageFactory.createMessage(m, parent, group, author,
					timestamp, raw);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			tryToClose(txn);
			throw new DbException(e);
		}
	}

	public Iterable<MessageId> getMessagesByAuthor(Connection txn, AuthorId a)
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
			return ids;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			tryToClose(txn);
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
			boolean found = rs.next();
			assert found;
			int count = rs.getInt(1);
			boolean more = rs.next();
			assert !more;
			rs.close();
			ps.close();
			return count;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			tryToClose(txn);
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
			boolean found = rs.next();
			assert found;
			byte[] group = rs.getBytes(1);
			boolean more = rs.next();
			assert !more;
			rs.close();
			ps.close();
			sql = "SELECT COUNT(messageId) FROM messages"
				+ " WHERE parentId = ? AND groupId = ?"
				+ " AND sendability > ZERO()";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setBytes(2, group);
			rs = ps.executeQuery();
			found = rs.next();
			assert found;
			int count = rs.getInt(1);
			more = rs.next();
			assert !more;
			rs.close();
			ps.close();
			return count;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			tryToClose(txn);
			throw new DbException(e);
		}
	}

	public Iterable<MessageId> getOldMessages(Connection txn, long capacity)
	throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT size, messageId FROM messages"
				+ " ORDER BY timestamp";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			List<MessageId> ids = new ArrayList<MessageId>();
			long total = 0L;
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
			tryToClose(txn);
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
			boolean found = rs.next();
			assert found;
			byte[] parent = rs.getBytes(1);
			boolean more = rs.next();
			assert !more;
			rs.close();
			ps.close();
			return new MessageId(parent);
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			tryToClose(txn);
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
			boolean more = rs.next();
			assert !more;
			rs.close();
			ps.close();
			return r;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			tryToClose(txn);
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
			boolean found = rs.next();
			assert found;
			int sendability = rs.getInt(1);
			boolean more = rs.next();
			assert !more;
			rs.close();
			ps.close();
			return sendability;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			tryToClose(txn);
			throw new DbException(e);
		}
	}

	public Iterable<MessageId> getSendableMessages(Connection txn,
			ContactId c, long capacity) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT size, messages.messageId FROM messages"
				+ " JOIN contactSubscriptions"
				+ " ON messages.groupId = contactSubscriptions.groupId"
				+ " JOIN statuses ON messages.messageId = statuses.messageId"
				+ " WHERE contactSubscriptions.contactId = ?"
				+ " AND statuses.contactId = ? AND status = ?"
				+ " AND sendability > ZERO()";
			// FIXME: Investigate the performance impact of "ORDER BY timestamp"
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setInt(2, c.getInt());
			ps.setShort(3, (short) Status.NEW.ordinal());
			rs = ps.executeQuery();
			List<MessageId> ids = new ArrayList<MessageId>();
			long total = 0;
			while(rs.next()) {
				int size = rs.getInt(1);
				if(total + size > capacity) break;
				ids.add(new MessageId(rs.getBytes(2)));
				total += size;
			}
			rs.close();
			ps.close();
			if(!ids.isEmpty()) {
				if(LOG.isLoggable(Level.FINE))
					LOG.fine(ids.size() + " sendable messages, " + total
							+ " bytes");
			}
			return ids;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			tryToClose(txn);
			throw new DbException(e);
		}
	}

	public Set<GroupId> getSubscriptions(Connection txn) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT groupId FROM localSubscriptions";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			Set<GroupId> ids = new HashSet<GroupId>();
			while(rs.next()) ids.add(new GroupId(rs.getBytes(1)));
			rs.close();
			ps.close();
			return ids;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			tryToClose(txn);
			throw new DbException(e);
		}
	}

	public Map<String, String> getTransports(Connection txn)
	throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT key, value FROM localTransports";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			Map<String, String> transports = new TreeMap<String, String>();
			while(rs.next()) transports.put(rs.getString(1), rs.getString(2));
			rs.close();
			ps.close();
			return transports;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			tryToClose(txn);
			throw new DbException(e);
		}
	}

	public Map<String, String> getTransports(Connection txn, ContactId c)
	throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT key, value FROM contactTransports"
				+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			Map<String, String> transports = new TreeMap<String, String>();
			while(rs.next()) transports.put(rs.getString(1), rs.getString(2));
			rs.close();
			ps.close();
			return transports;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			tryToClose(txn);
			throw new DbException(e);
		}
	}

	public void removeAckedBatch(Connection txn, ContactId c, BatchId b)
	throws DbException {
		// Increment the passover count of all older outstanding batches
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT timestamp FROM outstandingBatches"
				+ " WHERE contactId = ? AND batchId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setBytes(2, b.getBytes());
			rs = ps.executeQuery();
			boolean found = rs.next();
			assert found;
			long timestamp = rs.getLong(1);
			boolean more = rs.next();
			assert !more;
			rs.close();
			ps.close();
			sql = "UPDATE outstandingBatches SET passover = passover + ?"
				+ " WHERE contactId = ? AND timestamp < ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, 1);
			ps.setInt(2, c.getInt());
			ps.setLong(3, timestamp);
			int rowsAffected = ps.executeUpdate();
			assert rowsAffected >= 0;
			ps.close();
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			tryToClose(txn);
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
			int[] rowsAffectedArray = ps1.executeBatch();
			assert rowsAffectedArray.length == messages;
			for(int i = 0; i < rowsAffectedArray.length; i++) {
				assert rowsAffectedArray[i] <= 1;
			}
			ps1.close();
			// Cascade on delete
			sql = "DELETE FROM outstandingBatches WHERE batchId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, b.getBytes());
			int rowsAffected = ps.executeUpdate();
			assert rowsAffected <= 1;
			ps.close();
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			tryToClose(ps1);
			tryToClose(txn);
			throw new DbException(e);
		}
	}

	public Set<BatchId> removeBatchesToAck(Connection txn, ContactId c)
	throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT batchId FROM batchesToAck"
				+ " WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			rs = ps.executeQuery();
			Set<BatchId> ids = new HashSet<BatchId>();
			while(rs.next()) ids.add(new BatchId(rs.getBytes(1)));
			rs.close();
			ps.close();
			sql = "DELETE FROM batchesToAck WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			int rowsAffected = ps.executeUpdate();
			assert rowsAffected == ids.size();
			ps.close();
			return ids;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			tryToClose(txn);
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
			int rowsAffected = ps.executeUpdate();
			assert rowsAffected == 1;
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			tryToClose(txn);
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
			int rowsAffected = ps.executeUpdate();
			assert rowsAffected == 1;
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			tryToClose(txn);
			throw new DbException(e);
		}
	}

	public void removeSubscription(Connection txn, GroupId g)
	throws DbException {
		PreparedStatement ps = null;
		try {
			String sql = "DELETE FROM localSubscriptions WHERE groupId = ?";
			ps = txn.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			int rowsAffected = ps.executeUpdate();
			assert rowsAffected == 1;
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			tryToClose(txn);
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
				boolean more = rs.next();
				assert !more;
				rs.close();
				ps.close();
				sql = "UPDATE ratings SET rating = ? WHERE authorId = ?";
				ps = txn.prepareStatement(sql);
				ps.setShort(1, (short) r.ordinal());
				ps.setBytes(2, a.getBytes());
				int rowsAffected = ps.executeUpdate();
				assert rowsAffected == 1;
				ps.close();
			} else {
				rs.close();
				ps.close();
				old = Rating.UNRATED;
				sql = "INSERT INTO ratings (authorId, rating) VALUES (?, ?)";
				ps = txn.prepareStatement(sql);
				ps.setBytes(1, a.getBytes());
				ps.setShort(2, (short) r.ordinal());
				int rowsAffected = ps.executeUpdate();
				assert rowsAffected == 1;
				ps.close();
			}
			return old;
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			tryToClose(txn);
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
			int rowsAffected = ps.executeUpdate();
			assert rowsAffected == 1;
			ps.close();
		} catch(SQLException e) {
			tryToClose(ps);
			tryToClose(txn);
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
				boolean more = rs.next();
				assert !more;
				rs.close();
				ps.close();
				if(!old.equals(Status.SEEN) && !old.equals(s)) {
					sql = "UPDATE statuses SET status = ?"
						+ " WHERE messageId = ? AND contactId = ?";
					ps = txn.prepareStatement(sql);
					ps.setShort(1, (short) s.ordinal());
					ps.setBytes(2, m.getBytes());
					ps.setInt(3, c.getInt());
					int rowsAffected = ps.executeUpdate();
					assert rowsAffected == 1;
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
				int rowsAffected = ps.executeUpdate();
				assert rowsAffected == 1;
				ps.close();
			}
		} catch(SQLException e) {
			tryToClose(rs);
			tryToClose(ps);
			tryToClose(txn);
			throw new DbException(e);
		}
	}

	public void setTransports(Connection txn, Map<String, String> transports)
	throws DbException {
		PreparedStatement ps = null;
		try {
			// Delete any existing transports
			String sql = "DELETE FROM localTransports";
			ps = txn.prepareStatement(sql);
			ps.executeUpdate();
			ps.close();
			// Store the new transports
			if(transports != null) {
				sql = "INSERT INTO localTransports (key, value)"
					+ " VALUES (?, ?)";
				ps = txn.prepareStatement(sql);
				for(Entry<String, String> e : transports.entrySet()) {
					ps.setString(1, e.getKey());
					ps.setString(2, e.getValue());
					ps.addBatch();
				}
				int[] rowsAffectedArray = ps.executeBatch();
				assert rowsAffectedArray.length == transports.size();
				for(int i = 0; i < rowsAffectedArray.length; i++) {
					assert rowsAffectedArray[i] == 1;
				}
				ps.close();
			}
		} catch(SQLException e) {
			tryToClose(ps);
			tryToClose(txn);
			throw new DbException(e);
		}
	}

	public void setTransports(Connection txn, ContactId c,
			Map<String, String> transports) throws DbException {
		PreparedStatement ps = null;
		try {
			// Delete any existing transports
			String sql = "DELETE FROM contactTransports WHERE contactId = ?";
			ps = txn.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.executeUpdate();
			ps.close();
			// Store the new transports
			if(transports != null) {
				sql = "INSERT INTO contactTransports (contactId, key, value)"
					+ " VALUES (?, ?, ?)";
				ps = txn.prepareStatement(sql);
				ps.setInt(1, c.getInt());
				for(Entry<String, String> e : transports.entrySet()) {
					ps.setString(2, e.getKey());
					ps.setString(3, e.getValue());
					ps.addBatch();
				}
				int[] rowsAffectedArray = ps.executeBatch();
				assert rowsAffectedArray.length == transports.size();
				for(int i = 0; i < rowsAffectedArray.length; i++) {
					assert rowsAffectedArray[i] == 1;
				}
				ps.close();
			}
		} catch(SQLException e) {
			tryToClose(ps);
			tryToClose(txn);
			throw new DbException(e);
		}
	}
}
