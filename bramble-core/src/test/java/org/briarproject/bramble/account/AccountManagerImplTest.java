package org.briarproject.bramble.account;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.jmock.Expectations;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.annotation.Nullable;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.briarproject.bramble.test.TestUtils.deleteTestDirectory;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getTestDirectory;
import static org.briarproject.bramble.util.StringUtils.toHexString;
import static org.junit.Assert.assertEquals;

public class AccountManagerImplTest extends BrambleMockTestCase {

	private final DatabaseConfig databaseConfig =
			context.mock(DatabaseConfig.class);
	private final CryptoComponent crypto = context.mock(CryptoComponent.class);

	private final byte[] encryptedKey = getRandomBytes(123);
	private final String encryptedKeyHex = toHexString(encryptedKey);
	private final String oldEncryptedKeyHex = toHexString(getRandomBytes(123));
	private final File testDir = getTestDirectory();
	private final File keyDir = new File(testDir, "key");
	private final File keyFile = new File(keyDir, "db.key");
	private final File keyBackupFile = new File(keyDir, "db.key.bak");

	private AccountManagerImpl accountManager;

	@Before
	public void setUp() {
		context.checking(new Expectations() {{
			allowing(databaseConfig).getDatabaseKeyDirectory();
			will(returnValue(keyDir));
		}});
		assertTrue(keyDir.mkdirs());
		accountManager = new AccountManagerImpl(databaseConfig, crypto);
	}

	@Test
	public void testDbKeyIsLoadedFromPrimaryFile() throws Exception {
		assertFalse(keyFile.exists());
		assertFalse(keyBackupFile.exists());

		storeDatabaseKey(keyFile, encryptedKeyHex);

		assertTrue(keyFile.exists());
		assertFalse(keyBackupFile.exists());
		assertEquals(encryptedKeyHex, loadDatabaseKey(keyFile));

		assertEquals(encryptedKeyHex, accountManager.loadEncryptedDatabaseKey());

		assertTrue(keyFile.exists());
		assertFalse(keyBackupFile.exists());
		assertEquals(encryptedKeyHex, loadDatabaseKey(keyFile));
	}

	@Test
	public void testDbKeyIsLoadedFromBackupFile() throws Exception {
		assertFalse(keyFile.exists());
		assertFalse(keyBackupFile.exists());

		storeDatabaseKey(keyBackupFile, encryptedKeyHex);

		assertFalse(keyFile.exists());
		assertTrue(keyBackupFile.exists());
		assertEquals(encryptedKeyHex, loadDatabaseKey(keyBackupFile));

		assertEquals(encryptedKeyHex, accountManager.loadEncryptedDatabaseKey());

		assertFalse(keyFile.exists());
		assertTrue(keyBackupFile.exists());
		assertEquals(encryptedKeyHex, loadDatabaseKey(keyBackupFile));
	}

	@Test
	public void testDbKeyIsNullIfNotFound() {
		assertFalse(keyFile.exists());
		assertFalse(keyBackupFile.exists());

		assertNull(accountManager.loadEncryptedDatabaseKey());

		assertFalse(keyFile.exists());
		assertFalse(keyBackupFile.exists());
	}

	@Test
	public void testStoringDbKeyOverwritesPrimary() throws Exception {
		assertFalse(keyFile.exists());
		assertFalse(keyBackupFile.exists());

		storeDatabaseKey(keyFile, oldEncryptedKeyHex);

		assertTrue(keyFile.exists());
		assertFalse(keyBackupFile.exists());
		assertEquals(oldEncryptedKeyHex, loadDatabaseKey(keyFile));

		assertTrue(accountManager.storeEncryptedDatabaseKey(encryptedKeyHex));

		assertTrue(keyFile.exists());
		assertTrue(keyBackupFile.exists());
		assertEquals(encryptedKeyHex, loadDatabaseKey(keyFile));
		assertEquals(encryptedKeyHex, loadDatabaseKey(keyBackupFile));
	}

	@Test
	public void testStoringDbKeyOverwritesBackup() throws Exception {
		assertFalse(keyFile.exists());
		assertFalse(keyBackupFile.exists());

		storeDatabaseKey(keyBackupFile, oldEncryptedKeyHex);

		assertFalse(keyFile.exists());
		assertTrue(keyBackupFile.exists());
		assertEquals(oldEncryptedKeyHex, loadDatabaseKey(keyBackupFile));

		assertTrue(accountManager.storeEncryptedDatabaseKey(encryptedKeyHex));

		assertTrue(keyFile.exists());
		assertTrue(keyBackupFile.exists());
		assertEquals(encryptedKeyHex, loadDatabaseKey(keyFile));
		assertEquals(encryptedKeyHex, loadDatabaseKey(keyBackupFile));
	}

	private void storeDatabaseKey(File f, String hex) throws IOException {
		FileOutputStream out = new FileOutputStream(f);
		out.write(hex.getBytes("UTF-8"));
		out.flush();
		out.close();
	}

	@Nullable
	private String loadDatabaseKey(File f) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				new FileInputStream(f), "UTF-8"));
		String hex = reader.readLine();
		reader.close();
		return hex;
	}

	@After
	public void tearDown() {
		deleteTestDirectory(testDir);
	}
}
