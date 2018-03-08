package org.briarproject.bramble.db;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.ValidationManager.State;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.TestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static java.sql.Types.BINARY;
import static junit.framework.Assert.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.briarproject.bramble.api.sync.ValidationManager.State.DELIVERED;
import static org.briarproject.bramble.api.sync.ValidationManager.State.UNKNOWN;
import static org.briarproject.bramble.test.TestUtils.getMessage;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.junit.Assert.assertEquals;

public class Migration31_32Test extends BrambleTestCase {

	private static final String CREATE_GROUPS_STUB =
			"CREATE TABLE groups"
					+ " (groupId BINARY(32) NOT NULL,"
					+ " PRIMARY KEY (groupId))";

	private static final String CREATE_CONTACTS_STUB =
			"CREATE TABLE contacts"
					+ " (contactId INT NOT NULL,"
					+ " PRIMARY KEY (contactId))";

	private static final String CREATE_GROUP_VISIBILITIES_STUB =
			"CREATE TABLE groupVisibilities"
					+ " (contactId INT NOT NULL,"
					+ " groupId BINARY(32) NOT NULL,"
					+ " shared BOOLEAN NOT NULL,"
					+ " PRIMARY KEY (contactId, groupId))";

	private static final String CREATE_MESSAGES =
			"CREATE TABLE messages"
					+ " (messageId BINARY(32) NOT NULL,"
					+ " groupId BINARY(32) NOT NULL,"
					+ " timestamp BIGINT NOT NULL,"
					+ " state INT NOT NULL,"
					+ " shared BOOLEAN NOT NULL,"
					+ " length INT NOT NULL,"
					+ " raw BLOB," // Null if message has been deleted
					+ " PRIMARY KEY (messageId),"
					+ " FOREIGN KEY (groupId)"
					+ " REFERENCES groups (groupId)"
					+ " ON DELETE CASCADE)";

	private static final String CREATE_STATUSES_31 =
			"CREATE TABLE statuses"
					+ " (messageId BINARY(32) NOT NULL,"
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

	private final File testDir = TestUtils.getTestDirectory();
	private final File db = new File(testDir, "db");
	private final String url = "jdbc:h2:" + db.getAbsolutePath();
	private final GroupId groupId = new GroupId(getRandomId());
	private final GroupId groupId1 = new GroupId(getRandomId());
	private final ContactId contactId = new ContactId(123);
	private final ContactId contactId1 = new ContactId(234);
	private final Message message = getMessage(groupId);
	private final Message message1 = getMessage(groupId1);
	private final Message message2 = getMessage(groupId1);

	private Connection connection = null;

	@Before
	public void setUp() throws Exception {
		assertTrue(testDir.mkdirs());
		Class.forName("org.h2.Driver");
		connection = DriverManager.getConnection(url);
	}

	@After
	public void tearDown() throws Exception {
		if (connection != null) connection.close();
		TestUtils.deleteTestDirectory(testDir);
	}

	@Test
	public void testMigration() throws Exception {
		try {
			Statement s = connection.createStatement();
			s.execute(CREATE_GROUPS_STUB);
			s.execute(CREATE_CONTACTS_STUB);
			s.execute(CREATE_GROUP_VISIBILITIES_STUB);
			s.execute(CREATE_MESSAGES);
			s.execute(CREATE_STATUSES_31);
			s.close();

			addGroup(groupId);
			addMessage(message, DELIVERED, true, false);

			addGroup(groupId1);
			addMessage(message1, UNKNOWN, false, false);
			addMessage(message2, DELIVERED, true, true);

			addContact(contactId);

			addGroupVisibility(contactId, groupId, true);
			addStatus31(message.getId(), contactId);

			addGroupVisibility(contactId, groupId1, false);
			addStatus31(message1.getId(), contactId);
			addStatus31(message2.getId(), contactId);

			addContact(contactId1);

			addGroupVisibility(contactId1, groupId1, true);
			addStatus31(message1.getId(), contactId1);
			addStatus31(message2.getId(), contactId1);

			new Migration31_32().migrate(connection);

			assertTrue(containsStatus(message.getId(), contactId));
			Status32 status = getStatus32(message.getId(), contactId);
			assertEquals(groupId, status.groupId);
			assertEquals(message.getTimestamp(), status.timestamp);
			assertEquals(message.getLength(), status.length);
			assertEquals(DELIVERED, status.state);
			assertTrue(status.groupShared);
			assertTrue(status.messageShared);
			assertFalse(status.deleted);

			assertTrue(containsStatus(message1.getId(), contactId));
			status = getStatus32(message1.getId(), contactId);
			assertEquals(groupId1, status.groupId);
			assertEquals(message1.getTimestamp(), status.timestamp);
			assertEquals(message1.getLength(), status.length);
			assertEquals(UNKNOWN, status.state);
			assertFalse(status.groupShared);
			assertFalse(status.messageShared);
			assertFalse(status.deleted);

			assertTrue(containsStatus(message2.getId(), contactId));
			status = getStatus32(message2.getId(), contactId);
			assertEquals(groupId1, status.groupId);
			assertEquals(message2.getTimestamp(), status.timestamp);
			assertEquals(message2.getLength(), status.length);
			assertEquals(DELIVERED, status.state);
			assertFalse(status.groupShared);
			assertTrue(status.messageShared);
			assertTrue(status.deleted);

			assertFalse(containsStatus(message.getId(), contactId1));

			assertTrue(containsStatus(message1.getId(), contactId1));
			status = getStatus32(message1.getId(), contactId1);
			assertEquals(groupId1, status.groupId);
			assertEquals(message1.getTimestamp(), status.timestamp);
			assertEquals(message1.getLength(), status.length);
			assertEquals(UNKNOWN, status.state);
			assertTrue(status.groupShared);
			assertFalse(status.messageShared);
			assertFalse(status.deleted);

			assertTrue(containsStatus(message2.getId(), contactId1));
			status = getStatus32(message2.getId(), contactId1);
			assertEquals(groupId1, status.groupId);
			assertEquals(message2.getTimestamp(), status.timestamp);
			assertEquals(message2.getLength(), status.length);
			assertEquals(DELIVERED, status.state);
			assertTrue(status.groupShared);
			assertTrue(status.messageShared);
			assertTrue(status.deleted);
		} catch (SQLException e) {
			connection.close();
			throw e;
		}
	}

	private void addGroup(GroupId g) throws SQLException {
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO groups (groupId) VALUES (?)";
			ps = connection.prepareStatement(sql);
			ps.setBytes(1, g.getBytes());
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			if (ps != null) ps.close();
			throw e;
		}
	}

	private void addContact(ContactId c) throws SQLException {
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO contacts (contactId) VALUES (?)";
			ps = connection.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			if (ps != null) ps.close();
			throw e;
		}
	}

	private void addGroupVisibility(ContactId c, GroupId g, boolean shared)
			throws SQLException {
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO groupVisibilities"
					+ " (contactId, groupId, shared) VALUES (?, ?, ?)";
			ps = connection.prepareStatement(sql);
			ps.setInt(1, c.getInt());
			ps.setBytes(2, g.getBytes());
			ps.setBoolean(3, shared);
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			if (ps != null) ps.close();
			throw e;
		}
	}

	private void addMessage(Message m, State state, boolean shared,
			boolean deleted) throws SQLException {
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO messages (messageId, groupId, timestamp,"
					+ " state, shared, length, raw)"
					+ " VALUES (?, ?, ?, ?, ?, ?, ?)";
			ps = connection.prepareStatement(sql);
			ps.setBytes(1, m.getId().getBytes());
			ps.setBytes(2, m.getGroupId().getBytes());
			ps.setLong(3, m.getTimestamp());
			ps.setInt(4, state.getValue());
			ps.setBoolean(5, shared);
			byte[] raw = m.getRaw();
			ps.setInt(6, raw.length);
			if (deleted) ps.setNull(7, BINARY);
			else ps.setBytes(7, raw);
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			if (ps != null) ps.close();
			throw e;
		}
	}

	private void addStatus31(MessageId m, ContactId c) throws SQLException {
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO statuses (messageId, contactId, ack,"
					+ " seen, requested, expiry, txCount)"
					+ " VALUES (?, ?, FALSE, FALSE, FALSE, 0, 0)";
			ps = connection.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setInt(2, c.getInt());
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			if (ps != null) ps.close();
			throw e;
		}
	}

	private boolean containsStatus(MessageId m, ContactId c)
			throws SQLException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT COUNT (*) FROM statuses"
					+ " WHERE messageId = ? AND contactId = ?";
			ps = connection.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setInt(2, c.getInt());
			rs = ps.executeQuery();
			if (!rs.next()) throw new DbStateException();
			int count = rs.getInt(1);
			if (count < 0 || count > 1) throw new DbStateException();
			if (rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return count > 0;
		} catch (SQLException e) {
			if (rs != null) rs.close();
			if (ps != null) ps.close();
			throw e;
		}
	}

	private Status32 getStatus32(MessageId m, ContactId c) throws SQLException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT groupId, timestamp, length, state,"
					+ " groupShared, messageShared, deleted"
					+ " FROM statuses"
					+ " WHERE messageId = ? AND contactId = ?";
			ps = connection.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setInt(2, c.getInt());
			rs = ps.executeQuery();
			if (!rs.next()) throw new DbStateException();
			GroupId groupId = new GroupId(rs.getBytes(1));
			long timestamp = rs.getLong(2);
			int length = rs.getInt(3);
			State state = State.fromValue(rs.getInt(4));
			boolean groupShared = rs.getBoolean(5);
			boolean messageShared = rs.getBoolean(6);
			boolean deleted = rs.getBoolean(7);
			if (rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return new Status32(groupId, timestamp, length, state,
					groupShared, messageShared, deleted);
		} catch (SQLException e) {
			if (rs != null) rs.close();
			if (ps != null) ps.close();
			throw e;
		}
	}

	private static class Status32 {

		private final GroupId groupId;
		private final long timestamp;
		private final int length;
		private final State state;
		private final boolean groupShared, messageShared, deleted;

		private Status32(GroupId groupId, long timestamp, int length,
				State state, boolean groupShared, boolean messageShared,
				boolean deleted) {
			this.groupId = groupId;
			this.timestamp = timestamp;
			this.length = length;
			this.state = state;
			this.groupShared = groupShared;
			this.messageShared = messageShared;
			this.deleted = deleted;
		}
	}
}
