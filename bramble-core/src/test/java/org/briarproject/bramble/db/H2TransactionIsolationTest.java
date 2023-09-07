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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class H2TransactionIsolationTest extends BrambleTestCase {

	private static final String DROP_TABLE = "DROP TABLE foo IF EXISTS";
	private static final String CREATE_TABLE = "CREATE TABLE foo"
			+ " (_key INT NOT NULL,"
			+ " counter INT NOT NULL)";
	private static final String INSERT_ROW =
			"INSERT INTO foo (_key, counter) VALUES (1, 123)";
	private static final String GET_COUNTER =
			"SELECT counter FROM foo WHERE _key = 1";
	private static final String SET_COUNTER =
			"UPDATE foo SET counter = ? WHERE _key = 1";

	private final File testDir = TestUtils.getTestDirectory();
	private final File db = new File(testDir, "db");
	private final String url = "jdbc:h2:split:" + db.getAbsolutePath();

	@Before
	public void setUp() throws Exception {
		assertTrue(testDir.mkdirs());
		Class.forName("org.h2.Driver");
	}

	@After
	public void tearDown() throws Exception {
		TestUtils.deleteTestDirectory(testDir);
	}

	@Test
	public void testDoesNotReadUncommittedWritesWithMvcc() throws Exception {
		Connection connection = openConnection();
		try {
			createTableAndInsertRow(connection);
		} finally {
			connection.close();
		}
		// Start the first transaction
		Connection txn1 = openConnection();
		try {
			txn1.setAutoCommit(false);
			// The first transaction should read the initial value
			assertEquals(123, getCounter(txn1));
			// The first transaction updates the value but doesn't commit it
			assertEquals(1, setCounter(txn1, 234));
			// Start the second transaction
			Connection txn2 = openConnection();
			try {
				txn2.setAutoCommit(false);
				// The second transaction should still read the initial value
				assertEquals(123, getCounter(txn2));
				// Commit the second transaction
				txn2.commit();
			} finally {
				txn2.close();
			}
			// Commit the first transaction
			txn1.commit();
		} finally {
			txn1.close();
		}
	}

	@Test
	public void testLastWriterWinsWithMvcc() throws Exception {
		Connection connection = openConnection();
		try {
			createTableAndInsertRow(connection);
		} finally {
			connection.close();
		}
		// Start the first transaction
		Connection txn1 = openConnection();
		try {
			txn1.setAutoCommit(false);
			// The first transaction should read the initial value
			assertEquals(123, getCounter(txn1));
			// The first transaction updates the value but doesn't commit it
			assertEquals(1, setCounter(txn1, 234));
			// Start the second transaction
			Connection txn2 = openConnection();
			try {
				txn2.setAutoCommit(false);
				// The second transaction should still read the initial value
				assertEquals(123, getCounter(txn2));
				// Commit the first transaction
				txn1.commit();
				// The second transaction updates the value
				assertEquals(1, setCounter(txn2, 345));
				// Commit the second transaction
				txn2.commit();
			} finally {
				txn2.close();
			}
		} finally {
			txn1.close();
		}
		// The second transaction was the last writer, so it should win
		connection = openConnection();
		try {
			assertEquals(345, getCounter(connection));
		} finally {
			connection.close();
		}
	}

	@Test
	public void testLockTimeoutOnRowWithMvcc() throws Exception {
		Connection connection = openConnection();
		try {
			createTableAndInsertRow(connection);
		} finally {
			connection.close();
		}
		// Start the first transaction
		Connection txn1 = openConnection();
		try {
			txn1.setAutoCommit(false);
			// The first transaction should read the initial value
			assertEquals(123, getCounter(txn1));
			// Start the second transaction
			Connection txn2 = openConnection();
			try {
				txn2.setAutoCommit(false);
				// The second transaction should read the initial value
				assertEquals(123, getCounter(txn2));
				// The first transaction updates the value but doesn't commit it
				assertEquals(1, setCounter(txn1, 234));
				// The second transaction tries to update the value
				try {
					setCounter(txn2, 345);
					fail();
				} catch (SQLException expected) {
					// Expected: the row is locked by the first transaction
				}
				// Abort the transactions
				txn1.rollback();
				txn2.rollback();
			} finally {
				txn2.close();
			}
		} finally {
			txn1.close();
		}
	}

	private Connection openConnection() throws SQLException {
		return DriverManager.getConnection(url);
	}

	private void createTableAndInsertRow(Connection c) throws SQLException {
		Statement s = c.createStatement();
		s.executeUpdate(DROP_TABLE);
		s.executeUpdate(CREATE_TABLE);
		s.executeUpdate(INSERT_ROW);
		s.close();
	}

	private int getCounter(Connection c) throws SQLException {
		Statement s = c.createStatement();
		ResultSet rs = s.executeQuery(GET_COUNTER);
		assertTrue(rs.next());
		int counter = rs.getInt(1);
		assertFalse(rs.next());
		rs.close();
		s.close();
		return counter;
	}

	private int setCounter(Connection c, int counter)
			throws SQLException {
		PreparedStatement ps = c.prepareStatement(SET_COUNTER);
		ps.setInt(1, counter);
		int rowsAffected = ps.executeUpdate();
		ps.close();
		return rowsAffected;
	}
}
