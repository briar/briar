package org.briarproject.briar.android.controller;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.jmock.Expectations;
import org.junit.After;
import org.junit.Test;

import java.io.File;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.briarproject.bramble.test.TestUtils.deleteTestDirectory;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getTestDirectory;
import static org.briarproject.bramble.util.StringUtils.toHexString;
import static org.briarproject.briar.android.TestDatabaseKeyUtils.loadDatabaseKey;
import static org.briarproject.briar.android.TestDatabaseKeyUtils.storeDatabaseKey;

public class ConfigControllerImplTest extends BrambleMockTestCase {

	private final SharedPreferences prefs =
			context.mock(SharedPreferences.class);
	private final DatabaseConfig databaseConfig =
			context.mock(DatabaseConfig.class);
	private final Editor editor = context.mock(Editor.class);

	private final byte[] encryptedKey = getRandomBytes(123);
	private final String encryptedKeyHex = toHexString(encryptedKey);
	private final String oldEncryptedKeyHex = toHexString(getRandomBytes(123));
	private final File testDir = getTestDirectory();
	private final File keyDir = new File(testDir, "key");
	private final File keyFile = new File(keyDir, "db.key");
	private final File keyBackupFile = new File(keyDir, "db.key.bak");

	@Test
	public void testDbKeyIsMigratedFromPreferencesToFile() throws Exception {
		context.checking(new Expectations() {{
			oneOf(prefs).getString("key", null);
			will(returnValue(encryptedKeyHex));
			allowing(databaseConfig).getDatabaseKeyDirectory();
			will(returnValue(keyDir));
			oneOf(prefs).edit();
			will(returnValue(editor));
			oneOf(editor).remove("key");
			will(returnValue(editor));
			oneOf(editor).commit();
			will(returnValue(true));
		}});

		assertFalse(keyFile.exists());
		assertFalse(keyBackupFile.exists());

		ConfigControllerImpl c = new ConfigControllerImpl(prefs,
				databaseConfig);

		assertEquals(encryptedKeyHex, c.getEncryptedDatabaseKey());

		assertTrue(keyFile.exists());
		assertTrue(keyBackupFile.exists());
		assertEquals(encryptedKeyHex, loadDatabaseKey(keyFile));
		assertEquals(encryptedKeyHex, loadDatabaseKey(keyBackupFile));
	}

	@Test
	public void testDbKeyIsLoadedFromPrimaryFile() throws Exception {
		context.checking(new Expectations() {{
			oneOf(prefs).getString("key", null);
			will(returnValue(null));
			allowing(databaseConfig).getDatabaseKeyDirectory();
			will(returnValue(keyDir));
		}});

		assertFalse(keyFile.exists());
		assertFalse(keyBackupFile.exists());

		storeDatabaseKey(keyFile, encryptedKeyHex);

		assertTrue(keyFile.exists());
		assertFalse(keyBackupFile.exists());
		assertEquals(encryptedKeyHex, loadDatabaseKey(keyFile));

		ConfigControllerImpl c = new ConfigControllerImpl(prefs,
				databaseConfig);

		assertEquals(encryptedKeyHex, c.getEncryptedDatabaseKey());

		assertTrue(keyFile.exists());
		assertFalse(keyBackupFile.exists());
		assertEquals(encryptedKeyHex, loadDatabaseKey(keyFile));
	}

	@Test
	public void testDbKeyIsLoadedFromBackupFile() throws Exception {
		context.checking(new Expectations() {{
			oneOf(prefs).getString("key", null);
			will(returnValue(null));
			allowing(databaseConfig).getDatabaseKeyDirectory();
			will(returnValue(keyDir));
		}});

		assertFalse(keyFile.exists());
		assertFalse(keyBackupFile.exists());

		storeDatabaseKey(keyBackupFile, encryptedKeyHex);

		assertFalse(keyFile.exists());
		assertTrue(keyBackupFile.exists());
		assertEquals(encryptedKeyHex, loadDatabaseKey(keyBackupFile));

		ConfigControllerImpl c = new ConfigControllerImpl(prefs,
				databaseConfig);

		assertEquals(encryptedKeyHex, c.getEncryptedDatabaseKey());

		assertFalse(keyFile.exists());
		assertTrue(keyBackupFile.exists());
		assertEquals(encryptedKeyHex, loadDatabaseKey(keyBackupFile));
	}

	@Test
	public void testDbKeyIsNullIfNotFound() {
		context.checking(new Expectations() {{
			oneOf(prefs).getString("key", null);
			will(returnValue(null));
			allowing(databaseConfig).getDatabaseKeyDirectory();
			will(returnValue(keyDir));
		}});

		assertFalse(keyFile.exists());
		assertFalse(keyBackupFile.exists());

		ConfigControllerImpl c = new ConfigControllerImpl(prefs,
				databaseConfig);

		assertNull(c.getEncryptedDatabaseKey());

		assertFalse(keyFile.exists());
		assertFalse(keyBackupFile.exists());
	}

	@Test
	public void testStoringDbKeyOverwritesPrimary() throws Exception {
		context.checking(new Expectations() {{
			allowing(databaseConfig).getDatabaseKeyDirectory();
			will(returnValue(keyDir));
		}});

		assertFalse(keyFile.exists());
		assertFalse(keyBackupFile.exists());

		storeDatabaseKey(keyFile, oldEncryptedKeyHex);

		assertTrue(keyFile.exists());
		assertFalse(keyBackupFile.exists());
		assertEquals(oldEncryptedKeyHex, loadDatabaseKey(keyFile));

		ConfigController c = new ConfigControllerImpl(prefs,
				databaseConfig);

		assertTrue(c.storeEncryptedDatabaseKey(encryptedKeyHex));

		assertTrue(keyFile.exists());
		assertTrue(keyBackupFile.exists());
		assertEquals(encryptedKeyHex, loadDatabaseKey(keyFile));
		assertEquals(encryptedKeyHex, loadDatabaseKey(keyBackupFile));
	}

	@Test
	public void testStoringDbKeyOverwritesBackup() throws Exception {
		context.checking(new Expectations() {{
			allowing(databaseConfig).getDatabaseKeyDirectory();
			will(returnValue(keyDir));
		}});

		assertFalse(keyFile.exists());
		assertFalse(keyBackupFile.exists());

		storeDatabaseKey(keyBackupFile, oldEncryptedKeyHex);

		assertFalse(keyFile.exists());
		assertTrue(keyBackupFile.exists());
		assertEquals(oldEncryptedKeyHex, loadDatabaseKey(keyBackupFile));

		ConfigController c = new ConfigControllerImpl(prefs,
				databaseConfig);

		assertTrue(c.storeEncryptedDatabaseKey(encryptedKeyHex));

		assertTrue(keyFile.exists());
		assertTrue(keyBackupFile.exists());
		assertEquals(encryptedKeyHex, loadDatabaseKey(keyFile));
		assertEquals(encryptedKeyHex, loadDatabaseKey(keyBackupFile));
	}

	@After
	public void tearDown() {
		deleteTestDirectory(testDir);
	}
}
