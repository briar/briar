package org.briarproject.bramble.db;

import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.TestUtils;
import org.briarproject.bramble.util.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
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
import static org.junit.Assert.fail;

public abstract class BasicDatabaseTest extends BrambleTestCase {

	private static final int BATCH_SIZE = 100;

	private final File testDir = TestUtils.getTestDirectory();
	private final File db = new File(testDir, "db");

	protected abstract String getBinaryType();

	protected abstract String getDriverName();

	protected abstract Connection openConnection(File db, boolean encrypt)
			throws SQLException;

	protected abstract void shutdownDatabase(File db, boolean encrypt)
			throws SQLException;

	@Before
	public void setUp() throws Exception {
		testDir.mkdirs();
		Class.forName(getDriverName());
	}

	@Test
	public void testInsertUpdateAndDelete() throws Exception {
		Connection connection = openConnection(db, false);
		try {
			// Create the table
			createTable(connection);
			// Generate an ID and two names
			byte[] id = TestUtils.getRandomId();
			String oldName = StringUtils.getRandomString(50);
			String newName = StringUtils.getRandomString(50);
			// Insert the ID and old name into the table
			insertRow(connection, id, oldName);
			// Check that the old name can be retrieved using the ID
			assertTrue(rowExists(connection, id));
			assertEquals(oldName, getName(connection, id));
			// Update the name
			updateRow(connection, id, newName);
			// Check that the new name can be retrieved using the ID
			assertTrue(rowExists(connection, id));
			assertEquals(newName, getName(connection, id));
			// Delete the row from the table
			assertTrue(deleteRow(connection, id));
			// Check that the row no longer exists
			assertFalse(rowExists(connection, id));
			// Deleting the row again should have no effect
			assertFalse(deleteRow(connection, id));
		} finally {
			connection.close();
			shutdownDatabase(db, false);
		}
	}

	@Test
	public void testBatchInsertUpdateAndDelete() throws Exception {
		Connection connection = openConnection(db, false);
		try {
			// Create the table
			createTable(connection);
			// Generate some IDs and two sets of names
			byte[][] ids = new byte[BATCH_SIZE][];
			String[] oldNames = new String[BATCH_SIZE];
			String[] newNames = new String[BATCH_SIZE];
			for (int i = 0; i < BATCH_SIZE; i++) {
				ids[i] = TestUtils.getRandomId();
				oldNames[i] = StringUtils.getRandomString(50);
				newNames[i] = StringUtils.getRandomString(50);
			}
			// Insert the IDs and old names into the table as a batch
			insertBatch(connection, ids, oldNames);
			// Update the names as a batch
			updateBatch(connection, ids, newNames);
			// Check that the new names can be retrieved using the IDs
			for (int i = 0; i < BATCH_SIZE; i++) {
				assertTrue(rowExists(connection, ids[i]));
				assertEquals(newNames[i], getName(connection, ids[i]));
			}
			// Delete the rows as a batch
			boolean[] deleted = deleteBatch(connection, ids);
			// Check that the rows no longer exist
			for (int i = 0; i < BATCH_SIZE; i++) {
				assertTrue(deleted[i]);
				assertFalse(rowExists(connection, ids[i]));
			}
			// Deleting the rows again should have no effect
			deleted = deleteBatch(connection, ids);
			for (int i = 0; i < BATCH_SIZE; i++) assertFalse(deleted[i]);
		} finally {
			connection.close();
			shutdownDatabase(db, false);
		}
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
		Connection connection = openConnection(db, false);
		try {
			// Create the table
			createTable(connection);
			// Insert the rows
			insertRow(connection, first, "first");
			insertRow(connection, second, "second");
			insertRow(connection, third, "third");
			insertRow(connection, null, "null");
			// Check the ordering of the < operator: null is not comparable
			assertNull(getPredecessor(connection, first));
			assertEquals("first", getPredecessor(connection, second));
			assertEquals("second", getPredecessor(connection, third));
			assertNull(getPredecessor(connection, null));
			// Check the ordering of ORDER BY: nulls come first
			List<String> names = getNames(connection);
			assertEquals(4, names.size());
			assertEquals("null", names.get(0));
			assertEquals("first", names.get(1));
			assertEquals("second", names.get(2));
			assertEquals("third", names.get(3));
		} finally {
			connection.close();
			shutdownDatabase(db, false);
		}
	}

	@Test
	public void testDataIsFoundWithoutEncryption() throws Exception {
		testEncryption(false);
	}

	@Test
	public void testDataIsNotFoundWithEncryption() throws Exception {
		testEncryption(true);
	}

	private void testEncryption(boolean encrypt) throws Exception {
		byte[] sequence = new byte[] {'a', 'b', 'c', 'd', 'e', 'f', 'g'};
		Connection connection = openConnection(db, encrypt);
		try {
			createTable(connection);
			insertRow(connection, sequence, "abcdefg");
		} finally {
			connection.close();
			shutdownDatabase(db, encrypt);
		}
		try {
			if (findSequence(testDir, sequence) == encrypt) fail();
		} finally {
			shutdownDatabase(db, encrypt);
		}
	}

	private void createTable(Connection connection) throws SQLException {
		Statement s = connection.createStatement();
		String sql = "CREATE TABLE foo (uniqueId " + getBinaryType() + ","
				+ " name VARCHAR(100) NOT NULL)";
		s.executeUpdate(sql);
		s.close();
	}

	private void insertRow(Connection connection, byte[] id, String name)
			throws SQLException {
		String sql = "INSERT INTO foo (uniqueId, name) VALUES (?, ?)";
		PreparedStatement ps = connection.prepareStatement(sql);
		if (id == null) ps.setNull(1, BINARY);
		else ps.setBytes(1, id);
		ps.setString(2, name);
		int affected = ps.executeUpdate();
		assertEquals(1, affected);
		ps.close();
	}

	private boolean rowExists(Connection connection, byte[] id)
			throws SQLException {
		assertNotNull(id);
		String sql = "SELECT * FROM foo WHERE uniqueID = ?";
		PreparedStatement ps = connection.prepareStatement(sql);
		ps.setBytes(1, id);
		ResultSet rs = ps.executeQuery();
		boolean found = rs.next();
		assertFalse(rs.next());
		rs.close();
		ps.close();
		return found;
	}

	private String getName(Connection connection, byte[] id)
			throws SQLException {
		assertNotNull(id);
		String sql = "SELECT name FROM foo WHERE uniqueID = ?";
		PreparedStatement ps = connection.prepareStatement(sql);
		ps.setBytes(1, id);
		ResultSet rs = ps.executeQuery();
		assertTrue(rs.next());
		String name = rs.getString(1);
		assertFalse(rs.next());
		rs.close();
		ps.close();
		return name;
	}

	private void updateRow(Connection connection, byte[] id, String name)
			throws SQLException {
		String sql = "UPDATE foo SET name = ? WHERE uniqueId = ?";
		PreparedStatement ps = connection.prepareStatement(sql);
		if (id == null) ps.setNull(2, BINARY);
		else ps.setBytes(2, id);
		ps.setString(1, name);
		assertEquals(1, ps.executeUpdate());
		ps.close();
	}

	private boolean deleteRow(Connection connection, byte[] id)
			throws SQLException {
		String sql = "DELETE FROM foo WHERE uniqueId = ?";
		PreparedStatement ps = connection.prepareStatement(sql);
		if (id == null) ps.setNull(1, BINARY);
		else ps.setBytes(1, id);
		int affected = ps.executeUpdate();
		ps.close();
		return affected == 1;
	}

	private void insertBatch(Connection connection, byte[][] ids,
			String[] names) throws SQLException {
		assertEquals(ids.length, names.length);
		String sql = "INSERT INTO foo (uniqueId, name) VALUES (?, ?)";
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
	}

	private void updateBatch(Connection connection, byte[][] ids,
			String[] names) throws SQLException {
		assertEquals(ids.length, names.length);
		String sql = "UPDATE foo SET name = ? WHERE uniqueId = ?";
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
	}

	private boolean[] deleteBatch(Connection connection, byte[][] ids)
			throws SQLException {
		String sql = "DELETE FROM foo WHERE uniqueId = ?";
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
	}

	private String getPredecessor(Connection connection, byte[] id)
			throws SQLException {
		String sql = "SELECT name FROM foo WHERE uniqueId < ?"
				+ " ORDER BY uniqueId DESC";
		PreparedStatement ps = connection.prepareStatement(sql);
		ps.setBytes(1, id);
		ps.setMaxRows(1);
		ResultSet rs = ps.executeQuery();
		String name = rs.next() ? rs.getString(1) : null;
		assertFalse(rs.next());
		rs.close();
		ps.close();
		return name;
	}

	private List<String> getNames(Connection connection) throws SQLException {
		String sql = "SELECT name FROM foo ORDER BY uniqueId NULLS FIRST";
		List<String> names = new ArrayList<>();
		PreparedStatement ps = connection.prepareStatement(sql);
		ResultSet rs = ps.executeQuery();
		while (rs.next()) names.add(rs.getString(1));
		rs.close();
		ps.close();
		return names;
	}

	private boolean findSequence(File f, byte[] sequence) throws IOException {
		if (f.isDirectory()) {
			File[] children = f.listFiles();
			if (children != null)
				for (File child : children)
					if (findSequence(child, sequence)) return true;
			return false;
		} else if (f.isFile()) {
			FileInputStream in = new FileInputStream(f);
			try {
				int offset = 0;
				while (true) {
					int read = in.read();
					if (read == -1) return false;
					if (((byte) read) == sequence[offset]) {
						offset++;
						if (offset == sequence.length) return true;
					} else {
						offset = 0;
					}
				}
			} finally {
				in.close();
			}
		} else {
			return false;
		}
	}

	@After
	public void tearDown() throws Exception {
		TestUtils.deleteTestDirectory(testDir);
	}
}
