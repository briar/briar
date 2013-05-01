package net.sf.briar.db;

import static java.sql.Types.BINARY;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.sf.briar.BriarTestCase;
import net.sf.briar.TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class BasicH2Test extends BriarTestCase {

	private static final String CREATE_TABLE =
			"CREATE TABLE foo"
					+ " (uniqueId BINARY(32),"
					+ " name VARCHAR NOT NULL)";

	private final File testDir = TestUtils.getTestDirectory();
	private final File db = new File(testDir, "db");
	private final String url = "jdbc:h2:" + db.getPath();

	private Connection connection = null;

	@Before
	public void setUp() throws Exception {
		testDir.mkdirs();
		Class.forName("org.h2.Driver");
		connection = DriverManager.getConnection(url);
	}

	@Test
	public void testCreateTableAddRowAndRetrieve() throws Exception {
		// Create the table
		createTable(connection);
		// Generate an ID and a name
		byte[] id = new byte[32];
		new Random().nextBytes(id);
		String name = TestUtils.createRandomString(50);
		// Insert the ID and name into the table
		addRow(id, name);
		// Check that the name can be retrieved using the ID
		assertEquals(name, getName(id));
	}

	@Test
	public void testSortOrder() throws Exception {
		byte[] first = new byte[] {
				0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, -128
		};
		byte[] second = new byte[] {
				0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 0
		};
		byte[] third = new byte[] {
				0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 127
		};
		// Create the table
		createTable(connection);
		// Insert the rows
		addRow(first, "first");
		addRow(second, "second");
		addRow(third, "third");
		addRow(null, "null");
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

	@Test
	public void testCreateTableAddBatchAndRetrieve() throws Exception {
		// Create the table
		createTable(connection);
		// Generate some IDs and names
		byte[][] ids = new byte[10][32];
		String[] names = new String[10];
		Random random = new Random();
		for(int i = 0; i < 10; i++) {
			random.nextBytes(ids[i]);
			names[i] = TestUtils.createRandomString(50);
		}
		// Insert the IDs and names into the table as a batch
		addBatch(ids, names);
		// Check that the names can be retrieved using the IDs
		for(int i = 0; i < 10; i++) assertEquals(names[i], getName(ids[i]));
	}

	private void createTable(Connection connection) throws SQLException {
		try {
			Statement s = connection.createStatement();
			s.executeUpdate(CREATE_TABLE);
			s.close();
		} catch(SQLException e) {
			connection.close();
			throw e;
		}
	}

	private void addRow(byte[] id, String name) throws SQLException {
		String sql = "INSERT INTO foo (uniqueId, name) VALUES (?, ?)";
		try {
			PreparedStatement ps = connection.prepareStatement(sql);
			if(id == null) ps.setNull(1, BINARY);
			else ps.setBytes(1, id);
			ps.setString(2, name);
			int affected = ps.executeUpdate();
			assertEquals(1, affected);
			ps.close();
		} catch(SQLException e) {
			connection.close();
			throw e;
		}
	}

	private String getName(byte[] id) throws SQLException {
		String sql = "SELECT name FROM foo WHERE uniqueID = ?";
		try {
			PreparedStatement ps = connection.prepareStatement(sql);
			if(id != null) ps.setBytes(1, id);
			ResultSet rs = ps.executeQuery();
			assertTrue(rs.next());
			String name = rs.getString(1);
			assertFalse(rs.next());
			rs.close();
			ps.close();
			return name;
		} catch(SQLException e) {
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
		} catch(SQLException e) {
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
			while(rs.next()) names.add(rs.getString(1));
			rs.close();
			ps.close();
			return names;
		} catch(SQLException e) {
			connection.close();
			throw e;
		}
	}

	private void addBatch(byte[][] ids, String[] names) throws SQLException {
		assertEquals(ids.length, names.length);
		String sql = "INSERT INTO foo (uniqueId, name) VALUES (?, ?)";
		try {
			PreparedStatement ps = connection.prepareStatement(sql);
			for(int i = 0; i < ids.length; i++) {
				if(ids[i] == null) ps.setNull(1, BINARY);
				else ps.setBytes(1, ids[i]);
				ps.setString(2, names[i]);
				ps.addBatch();
			}
			int[] batchAffected = ps.executeBatch();
			assertEquals(ids.length, batchAffected.length);
			for(int i = 0; i < batchAffected.length; i++) {
				assertEquals(1, batchAffected[i]);
			}
			ps.close();
		} catch(SQLException e) {
			connection.close();
			throw e;
		}
	}

	@After
	public void tearDown() throws Exception {
		if(connection != null) connection.close();
		TestUtils.deleteTestDirectory(testDir);
	}
}
