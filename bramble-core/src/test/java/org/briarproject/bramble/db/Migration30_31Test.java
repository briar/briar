package org.briarproject.bramble.db;

import org.briarproject.bramble.api.db.Metadata;
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
import java.util.Map.Entry;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.briarproject.bramble.api.sync.ValidationManager.State.DELIVERED;
import static org.briarproject.bramble.api.sync.ValidationManager.State.UNKNOWN;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class Migration30_31Test extends BrambleTestCase {

	private static final String CREATE_GROUPS_STUB =
			"CREATE TABLE groups"
					+ " (groupID BINARY(32) NOT NULL,"
					+ " PRIMARY KEY (groupId))";

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

	private static final String CREATE_MESSAGE_METADATA_30 =
			"CREATE TABLE messageMetadata"
					+ " (messageId BINARY(32) NOT NULL,"
					+ " key VARCHAR NOT NULL,"
					+ " value BINARY NOT NULL,"
					+ " PRIMARY KEY (messageId, key),"
					+ " FOREIGN KEY (messageId)"
					+ " REFERENCES messages (messageId)"
					+ " ON DELETE CASCADE)";

	private final File testDir = TestUtils.getTestDirectory();
	private final File db = new File(testDir, "db");
	private final String url = "jdbc:h2:" + db.getAbsolutePath();
	private final GroupId groupId = new GroupId(getRandomId());
	private final GroupId groupId1 = new GroupId(getRandomId());
	private final Message message = TestUtils.getMessage(groupId);
	private final Message message1 = TestUtils.getMessage(groupId1);
	private final Metadata meta = new Metadata(), meta1 = new Metadata();

	private Connection connection = null;

	public Migration30_31Test() {
		for (int i = 0; i < 10; i++) {
			meta.put(getRandomString(123 + i), getRandomBytes(123 + i));
			meta1.put(getRandomString(123 + i), getRandomBytes(123 + i));
		}
	}

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
			s.execute(CREATE_MESSAGES);
			s.execute(CREATE_MESSAGE_METADATA_30);
			s.close();

			addGroup(groupId);
			addMessage(message, DELIVERED, true);
			addMessageMetadata30(message, meta);
			assertMetadataEquals(meta, getMessageMetadata(message.getId()));

			addGroup(groupId1);
			addMessage(message1, UNKNOWN, false);
			addMessageMetadata30(message1, meta1);
			assertMetadataEquals(meta1, getMessageMetadata(message1.getId()));

			new Migration30_31().migrate(connection);

			assertMetadataEquals(meta, getMessageMetadata(message.getId()));
			for (String key : meta.keySet()) {
				GroupId g = getMessageMetadataGroupId31(message.getId(), key);
				assertEquals(groupId, g);
				State state = getMessageMetadataState31(message.getId(), key);
				assertEquals(DELIVERED, state);
			}

			assertMetadataEquals(meta1, getMessageMetadata(message1.getId()));
			for (String key : meta1.keySet()) {
				GroupId g = getMessageMetadataGroupId31(message1.getId(), key);
				assertEquals(groupId1, g);
				State state = getMessageMetadataState31(message1.getId(), key);
				assertEquals(UNKNOWN, state);
			}
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

	private void addMessage(Message m, State state, boolean shared)
			throws SQLException {
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
			ps.setBytes(7, raw);
			int affected = ps.executeUpdate();
			if (affected != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			if (ps != null) ps.close();
			throw e;
		}
	}

	private void addMessageMetadata30(Message m, Metadata meta)
			throws SQLException {
		PreparedStatement ps = null;
		try {
			String sql = "INSERT INTO messageMetadata"
					+ " (messageId, key, value)"
					+ " VALUES (?, ?, ?)";
			ps = connection.prepareStatement(sql);
			ps.setBytes(1, m.getId().getBytes());
			for (Entry<String, byte[]> e : meta.entrySet()) {
				ps.setString(2, e.getKey());
				ps.setBytes(3, e.getValue());
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			if (batchAffected.length != meta.size())
				throw new DbStateException();
			for (int rows : batchAffected)
				if (rows != 1) throw new DbStateException();
			ps.close();
		} catch (SQLException e) {
			if (ps != null) ps.close();
			throw e;
		}
	}

	private Metadata getMessageMetadata(MessageId m) throws SQLException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT key, value FROM messageMetadata"
					+ " WHERE messageId = ?";
			ps = connection.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			rs = ps.executeQuery();
			Metadata meta = new Metadata();
			while (rs.next()) meta.put(rs.getString(1), rs.getBytes(2));
			rs.close();
			ps.close();
			return meta;
		} catch (SQLException e) {
			if (rs != null) rs.close();
			if (ps != null) ps.close();
			throw e;
		}
	}

	private GroupId getMessageMetadataGroupId31(MessageId m, String key)
		throws SQLException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT groupId FROM messageMetadata"
					+ " WHERE messageId = ? AND key = ?";
			ps = connection.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setString(2, key);
			rs = ps.executeQuery();
			if (!rs.next()) throw new DbStateException();
			GroupId g = new GroupId(rs.getBytes(1));
			if (rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return g;
		} catch (SQLException e) {
			if (rs != null) rs.close();
			if (ps != null) ps.close();
			throw e;
		}
	}

	private State getMessageMetadataState31(MessageId m, String key)
		throws SQLException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "SELECT state FROM messageMetadata"
					+ " WHERE messageId = ? AND key = ?";
			ps = connection.prepareStatement(sql);
			ps.setBytes(1, m.getBytes());
			ps.setString(2, key);
			rs = ps.executeQuery();
			if (!rs.next()) throw new DbStateException();
			State state = State.fromValue(rs.getInt(1));
			if (rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			return state;
		} catch (SQLException e) {
			if (rs != null) rs.close();
			if (ps != null) ps.close();
			throw e;
		}
	}

	private void assertMetadataEquals(Metadata expected, Metadata actual) {
		assertEquals(expected.size(), actual.size());
		for (Entry<String, byte[]> e : expected.entrySet()) {
			byte[] value = actual.get(e.getKey());
			assertNotNull(value);
			assertArrayEquals(e.getValue(), value);
		}
	}
}
