package net.sf.briar.db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;

import junit.framework.TestCase;
import net.sf.briar.TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class BasicH2Test extends TestCase {

	private static final String CREATE_TABLE =
		"CREATE TABLE foo"
		+ " (uniqueId BINARY(32) NOT NULL,"
		+ " name VARCHAR NOT NULL,"
		+ " PRIMARY KEY (uniqueId))";

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
	public void testCreateTableAndAddRow() throws Exception {
		// Create the table
		createTable(connection);
		// Generate a unique ID
		byte[] uniqueId = new byte[32];
		new Random().nextBytes(uniqueId);
		// Insert the unique ID and name into the table
		addRow(uniqueId, "foo");
	}

	@Test
	public void testCreateTableAddAndRetrieveRow() throws Exception {
		// Create the table
		createTable(connection);
		// Generate a unique ID
		byte[] uniqueId = new byte[32];
		new Random().nextBytes(uniqueId);
		// Insert the unique ID and name into the table
		addRow(uniqueId, "foo");
		// Check that the name can be retrieved using the unique ID
		assertEquals("foo", getName(uniqueId));
	}
	
	private void addRow(byte[] uniqueId, String name) throws SQLException {
		String sql = "INSERT INTO foo (uniqueId, name) VALUES (?, ?)";
		PreparedStatement ps = null;
		try {
			ps = connection.prepareStatement(sql);
			ps.setBytes(1, uniqueId);
			ps.setString(2, name);
			int rowsAffected = ps.executeUpdate();
			ps.close();
			assertEquals(1, rowsAffected);
		} catch(SQLException e) {
			connection.close();
			throw e;
		}
	}

	private String getName(byte[] uniqueId) throws SQLException {
		String sql = "SELECT name FROM foo WHERE uniqueID = ?";
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			ps = connection.prepareStatement(sql);
			ps.setBytes(1, uniqueId);
			rs = ps.executeQuery();
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

	private void createTable(Connection connection) throws SQLException {
		Statement s;
		try {
			s = connection.createStatement();
			s.executeUpdate(CREATE_TABLE);
			s.close();
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
