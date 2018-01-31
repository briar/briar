package org.briarproject.bramble.db;

import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.system.SystemClock;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.TestDatabaseConfig;
import org.briarproject.bramble.test.TestUtils;
import org.jmock.Expectations;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.sql.Connection;
import java.util.Collection;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.briarproject.bramble.db.DatabaseConstants.DB_SETTINGS_NAMESPACE;
import static org.briarproject.bramble.db.DatabaseConstants.SCHEMA_VERSION_KEY;
import static org.briarproject.bramble.db.JdbcDatabase.CODE_SCHEMA_VERSION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@NotNullByDefault
public abstract class DatabaseMigrationTest extends BrambleMockTestCase {

	private final File testDir = TestUtils.getTestDirectory();
	@SuppressWarnings("unchecked")
	private final Migration<Connection> migration =
			context.mock(Migration.class);

	protected final DatabaseConfig config =
			new TestDatabaseConfig(testDir, 1024 * 1024);
	protected final Clock clock = new SystemClock();

	abstract Database<Connection> createDatabase(
			Collection<Migration<Connection>> migrations) throws Exception;

	@Before
	public void setUp() {
		assertTrue(testDir.mkdirs());
	}

	@After
	public void tearDown() {
		TestUtils.deleteTestDirectory(testDir);
	}

	@Test
	public void testDoesNotRunMigrationsWhenCreatingDatabase()
			throws Exception {
		Database<Connection> db = createDatabase(singletonList(migration));
		assertFalse(db.open());
		assertEquals(CODE_SCHEMA_VERSION, getDataSchemaVersion(db));
		db.close();
	}

	@Test(expected = DbException.class)
	public void testThrowsExceptionIfDataSchemaVersionIsMissing()
			throws Exception {
		// Open the DB for the first time
		Database<Connection> db = createDatabase(singletonList(migration));
		assertFalse(db.open());
		assertEquals(CODE_SCHEMA_VERSION, getDataSchemaVersion(db));
		// Override the data schema version
		setDataSchemaVersion(db, -1);
		db.close();
		// Reopen the DB - an exception should be thrown
		db = createDatabase(singletonList(migration));
		db.open();
	}

	@Test
	public void testDoesNotRunMigrationsIfSchemaVersionsMatch()
			throws Exception {
		// Open the DB for the first time
		Database<Connection> db = createDatabase(singletonList(migration));
		assertFalse(db.open());
		assertEquals(CODE_SCHEMA_VERSION, getDataSchemaVersion(db));
		db.close();
		// Reopen the DB - migrations should not be run
		db = createDatabase(singletonList(migration));
		assertTrue(db.open());
		assertEquals(CODE_SCHEMA_VERSION, getDataSchemaVersion(db));
		db.close();
	}

	@Test(expected = DbException.class)
	public void testThrowsExceptionIfDataIsNewerThanCode() throws Exception {
		// Open the DB for the first time
		Database<Connection> db = createDatabase(singletonList(migration));
		assertFalse(db.open());
		assertEquals(CODE_SCHEMA_VERSION, getDataSchemaVersion(db));
		// Override the data schema version
		setDataSchemaVersion(db, CODE_SCHEMA_VERSION + 1);
		db.close();
		// Reopen the DB - an exception should be thrown
		db = createDatabase(singletonList(migration));
		db.open();
	}

	@Test(expected = DbException.class)
	public void testThrowsExceptionIfCodeIsNewerThanDataAndNoMigrations()
			throws Exception {
		// Open the DB for the first time
		Database<Connection> db = createDatabase(emptyList());
		assertFalse(db.open());
		assertEquals(CODE_SCHEMA_VERSION, getDataSchemaVersion(db));
		setDataSchemaVersion(db, CODE_SCHEMA_VERSION - 1);
		db.close();
		// Reopen the DB - an exception should be thrown
		db = createDatabase(emptyList());
		db.open();
	}

	@Test(expected = DbException.class)
	public void testThrowsExceptionIfCodeIsNewerThanDataAndNoSuitableMigration()
			throws Exception {
		context.checking(new Expectations() {{
			oneOf(migration).getStartVersion();
			will(returnValue(CODE_SCHEMA_VERSION - 2));
			oneOf(migration).getEndVersion();
			will(returnValue(CODE_SCHEMA_VERSION));
		}});

		// Open the DB for the first time
		Database<Connection> db = createDatabase(singletonList(migration));
		assertFalse(db.open());
		assertEquals(CODE_SCHEMA_VERSION, getDataSchemaVersion(db));
		// Override the data schema version
		setDataSchemaVersion(db, CODE_SCHEMA_VERSION - 1);
		db.close();
		// Reopen the DB - an exception should be thrown
		db = createDatabase(singletonList(migration));
		db.open();
	}

	@Test
	public void testRunsMigrationIfCodeIsNewerThanDataAndSuitableMigration()
			throws Exception {
		context.checking(new Expectations() {{
			oneOf(migration).getStartVersion();
			will(returnValue(CODE_SCHEMA_VERSION - 1));
			oneOf(migration).getEndVersion();
			will(returnValue(CODE_SCHEMA_VERSION));
			oneOf(migration).migrate(with(any(Connection.class)));
		}});

		// Open the DB for the first time
		Database<Connection> db = createDatabase(singletonList(migration));
		assertFalse(db.open());
		// Override the data schema version
		setDataSchemaVersion(db, CODE_SCHEMA_VERSION - 1);
		db.close();
		// Reopen the DB - the migration should be run
		db = createDatabase(singletonList(migration));
		assertTrue(db.open());
		db.close();
	}

	private int getDataSchemaVersion(Database<Connection> db)
			throws Exception {
		Connection txn = db.startTransaction();
		Settings s = db.getSettings(txn, DB_SETTINGS_NAMESPACE);
		db.commitTransaction(txn);
		return s.getInt(SCHEMA_VERSION_KEY, -1);
	}

	private void setDataSchemaVersion(Database<Connection> db, int version)
			throws Exception {
		Settings s = new Settings();
		s.putInt(SCHEMA_VERSION_KEY, version);
		Connection txn = db.startTransaction();
		db.mergeSettings(txn, s, DB_SETTINGS_NAMESPACE);
		db.commitTransaction(txn);
	}
}
