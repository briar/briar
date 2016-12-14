package org.briarproject.bramble.db;

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
import java.util.ArrayList;
import java.util.List;

import static java.sql.Types.BINARY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BasicH2Test extends BrambleTestCase {

	private static final String CREATE_TABLE =
			"CREATE TABLE foo (uniqueId BINARY(32), name VARCHAR NOT NULL)";
	private static final int BATCH_SIZE = 100;

	private final File testDir = TestUtils.getTestDirectory();
	private final File db = new File(testDir, "db");
	private final String url = "jdbc:h2:" + db.getAbsolutePath();

	private Connection connection = null;

	@Before
	public void setUp() throws Exception {
		testDir.mkdirs();
		Class.forName("org.h2.Driver");
		connection = DriverManager.getConnection(url);
	}

	@Test
	public void testInsertUpdateAndDelete() throws Exception {
		// Create the table
		createTable(connection);
		// Generate an ID and two names
		byte[] id = TestUtils.getRandomId();
		String oldName = TestUtils.getRandomString(50);
		String newName = TestUtils.getRandomString(50);
		// Insert the ID and old name into the table
		insertRow(id, oldName);
		// Check that the old name can be retrieved using the ID
		assertTrue(rowExists(id));
		assertEquals(oldName, getName(id));
		// Update the name
		updateRow(id, newName);
		// Check that the new name can be retrieved using the ID
		assertTrue(rowExists(id));
		assertEquals(newName, getName(id));
		// Delete the row from the table
		assertTrue(deleteRow(id));
		// Check that the row no longer exists
		assertFalse(rowExists(id));
		// Deleting the row again should have no effect
		assertFalse(deleteRow(id));
	}

	@Test
	public void testBatchInsertUpdateAndDelete() throws Exception {
		// Create the table
		createTable(connection);
		// Generate some IDs and two sets of names
		byte[][] ids = new byte[BATCH_SIZE][];
		String[] oldNames = new String[BATCH_SIZE];
		String[] newNames = new String[BATCH_SIZE];
		for (int i = 0; i < BATCH_SIZE; i++) {
			ids[i] = TestUtils.getRandomId();
			oldNames[i] = TestUtils.getRandomString(50);
			newNames[i] = TestUtils.getRandomString(50);
		}
		// Insert the IDs and old names into the table as a batch
		insertBatch(ids, oldNames);
		// Update the names as a batch
		updateBatch(ids, newNames);
		// Check that the new names can be retrieved using the IDs
		for (int i = 0; i < BATCH_SIZE; i++) {
			assertTrue(rowExists(ids[i]));
			assertEquals(newNames[i], getName(ids[i]));
		}
		// Delete the rows as a batch
		boolean[] deleted = deleteBatch(ids);
		// Check that the rows no longer exist
		for (int i = 0; i < BATCH_SIZE; i++) {
			assertTrue(deleted[i]);
			assertFalse(rowExists(ids[i]));
		}
		// Deleting the rows again should have no effect
		deleted = deleteBatch(ids);
		for (int i = 0; i < BATCH_SIZE; i++) assertFalse(deleted[i]);
	}

	@Test
	public void testSortOrder() throws Exception {
		byte[] first = new byte[] {
				0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 0
		};
		byte[] second = new byte[] {
				0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 127
		};
		byte[] third = new byte[] {
				0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, (byte) 255
		};
		// Create the table
		createTable(connection);
		// Insert the rows
		insertRow(first, "first");
		insertRow(second, "second");
		insertRow(third, "third");
		insertRow(null, "null");
		// Check the ordering of the < operator: the null ID is not comparable
		assertNull(getPredecessor(first));
		assertEquals("first", getPredecessor(second));
		assertEquals("second", getPredecessor(third));
		assertNull(getPredecessor(null));
		// Check the ordering of ORDER BY: nulls come first
		List<String> names = getNames();
		assertEquals(4, names.size());
		assertEquals("null", names.get(0));
		assertEquals("first", names.get(1));
		assertEquals("second", names.get(2));
		assertEquals("third", names.get(3));
	}

	private void createTable(Connection connection) throws SQLException {
		try {
			Statement s = connection.createStatement();
			s.executeUpdate(CREATE_TABLE);
			s.close();
		} catch (SQLException e) {
			connection.close();
			throw e;
		}
	}

	private void insertRow(byte[] id, String name) throws SQLException {
		String sql = "INSERT INTO foo (uniqueId, name) VALUES (?, ?)";
		try {
			PreparedStatement ps = connection.prepareStatement(sql);
			if (id == null) ps.setNull(1, BINARY);
			else ps.setBytes(1, id);
			ps.setString(2, name);
			int affected = ps.executeUpdate();
			assertEquals(1, affected);
			ps.close();
		} catch (SQLException e) {
			connection.close();
			throw e;
		}
	}

	private boolean rowExists(byte[] id) throws SQLException {
		assertNotNull(id);
		String sql = "SELECT NULL FROM foo WHERE uniqueID = ?";
		try {
			PreparedStatement ps = connection.prepareStatement(sql);
			ps.setBytes(1, id);
			ResultSet rs = ps.executeQuery();
			boolean found = rs.next();
			assertFalse(rs.next());
			rs.close();
			ps.close();
			return found;
		} catch (SQLException e) {
			connection.close();
			throw e;
		}
	}

	private String getName(byte[] id) throws SQLException {
		assertNotNull(id);
		String sql = "SELECT name FROM foo WHERE uniqueID = ?";
		try {
			PreparedStatement ps = connection.prepareStatement(sql);
			ps.setBytes(1, id);
			ResultSet rs = ps.executeQuery();
			assertTrue(rs.next());
			String name = rs.getString(1);
			assertFalse(rs.next());
			rs.close();
			ps.close();
			return name;
		} catch (SQLException e) {
			connection.close();
			throw e;
		}
	}

	private void updateRow(byte[] id, String name) throws SQLException {
		String sql = "UPDATE foo SET name = ? WHERE uniqueId = ?";
		try {
			PreparedStatement ps = connection.prepareStatement(sql);
			if (id == null) ps.setNull(2, BINARY);
			else ps.setBytes(2, id);
			ps.setString(1, name);
			assertEquals(1, ps.executeUpdate());
			ps.close();
		} catch (SQLException e) {
			connection.close();
			throw e;
		}
	}

	private boolean deleteRow(byte[] id) throws SQLException {
		String sql = "DELETE FROM foo WHERE uniqueId = ?";
		try {
			PreparedStatement ps = connection.prepareStatement(sql);
			if (id == null) ps.setNull(1, BINARY);
			else ps.setBytes(1, id);
			int affected = ps.executeUpdate();
			ps.close();
			return affected == 1;
		} catch (SQLException e) {
			connection.close();
			throw e;
		}
	}

	private void insertBatch(byte[][] ids, String[] names) throws SQLException {
		assertEquals(ids.length, names.length);
		String sql = "INSERT INTO foo (uniqueId, name) VALUES (?, ?)";
		try {
			PreparedStatement ps = connection.prepareStatement(sql);
			for (int i = 0; i < ids.length; i++) {
				if (ids[i] == null) ps.setNull(1, BINARY);
				else ps.setBytes(1, ids[i]);
				ps.setString(2, names[i]);
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			assertEquals(ids.length, batchAffected.length);
			for (int affected : batchAffected) assertEquals(1, affected);
			ps.close();
		} catch (SQLException e) {
			connection.close();
			throw e;
		}
	}

	private void updateBatch(byte[][] ids, String[] names) throws SQLException {
		assertEquals(ids.length, names.length);
		String sql = "UPDATE foo SET name = ? WHERE uniqueId = ?";
		try {
			PreparedStatement ps = connection.prepareStatement(sql);
			for (int i = 0; i < ids.length; i++) {
				if (ids[i] == null) ps.setNull(2, BINARY);
				else ps.setBytes(2, ids[i]);
				ps.setString(1, names[i]);
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			assertEquals(ids.length, batchAffected.length);
			for (int affected : batchAffected) assertEquals(1, affected);
			ps.close();
		} catch (SQLException e) {
			connection.close();
			throw e;
		}
	}

	private boolean[] deleteBatch(byte[][] ids) throws SQLException {
		String sql = "DELETE FROM foo WHERE uniqueId = ?";
		try {
			PreparedStatement ps = connection.prepareStatement(sql);
			for (byte[] id : ids) {
				if (id == null) ps.setNull(1, BINARY);
				else ps.setBytes(1, id);
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			assertEquals(ids.length, batchAffected.length);
			boolean[] ret = new boolean[ids.length];
			for (int i = 0; i < batchAffected.length; i++)
				ret[i] = batchAffected[i] == 1;
			ps.close();
			return ret;
		} catch (SQLException e) {
			connection.close();
			throw e;
		}
	}

	private String getPredecessor(byte[] id) throws SQLException {
		String sql = "SELECT name FROM foo WHERE uniqueId < ?"
				+ " ORDER BY uniqueId DESC LIMIT ?";
		try {
			PreparedStatement ps = connection.prepareStatement(sql);
			ps.setBytes(1, id);
			ps.setInt(2, 1);
			ResultSet rs = ps.executeQuery();
			String name = rs.next() ? rs.getString(1) : null;
			assertFalse(rs.next());
			rs.close();
			ps.close();
			return name;
		} catch (SQLException e) {
			connection.close();
			throw e;
		}
	}

	private List<String> getNames() throws SQLException {
		String sql = "SELECT name FROM foo ORDER BY uniqueId";
		List<String> names = new ArrayList<String>();
		try {
			PreparedStatement ps = connection.prepareStatement(sql);
			ResultSet rs = ps.executeQuery();
			while (rs.next()) names.add(rs.getString(1));
			rs.close();
			ps.close();
			return names;
		} catch (SQLException e) {
			connection.close();
			throw e;
		}
	}

	@After
	public void tearDown() throws Exception {
		if (connection != null) connection.close();
		TestUtils.deleteTestDirectory(testDir);
	}
}
