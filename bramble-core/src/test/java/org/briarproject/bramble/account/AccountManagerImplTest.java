package org.briarproject.bramble.account;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
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
import static org.briarproject.bramble.test.TestUtils.getLocalAuthor;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.briarproject.bramble.test.TestUtils.getTestDirectory;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.briarproject.bramble.util.StringUtils.toHexString;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class AccountManagerImplTest extends BrambleMockTestCase {

	private final DatabaseConfig databaseConfig =
			context.mock(DatabaseConfig.class);
	private final CryptoComponent crypto = context.mock(CryptoComponent.class);
	private final IdentityManager identityManager =
			context.mock(IdentityManager.class);

	private final SecretKey key = getSecretKey();
	private final byte[] encryptedKey = getRandomBytes(123);
	private final String encryptedKeyHex = toHexString(encryptedKey);
	private final byte[] newEncryptedKey = getRandomBytes(123);
	private final String newEncryptedKeyHex = toHexString(newEncryptedKey);
	private final LocalAuthor localAuthor = getLocalAuthor();
	private final String authorName = localAuthor.getName();
	private final String password = getRandomString(10);
	private final String newPassword = getRandomString(10);
	private final File testDir = getTestDirectory();
	private final File dbDir = new File(testDir, "db");
	private final File keyDir = new File(testDir, "key");
	private final File keyFile = new File(keyDir, "db.key");
	private final File keyBackupFile = new File(keyDir, "db.key.bak");

	private AccountManagerImpl accountManager;

	@Before
	public void setUp() {
		context.checking(new Expectations() {{
			allowing(databaseConfig).getDatabaseDirectory();
			will(returnValue(dbDir));
			allowing(databaseConfig).getDatabaseKeyDirectory();
			will(returnValue(keyDir));
		}});

		accountManager =
				new AccountManagerImpl(databaseConfig, crypto, identityManager);

		assertFalse(keyFile.exists());
		assertFalse(keyBackupFile.exists());
	}

	@Test
	public void testSignInReturnsFalseIfDbKeyCannotBeLoaded() {
		assertFalse(accountManager.signIn(password));
		assertFalse(accountManager.hasDatabaseKey());

		assertFalse(keyFile.exists());
		assertFalse(keyBackupFile.exists());
	}

	@Test
	public void testSignInReturnsFalseIfPasswordIsWrong() throws Exception {
		context.checking(new Expectations() {{
			oneOf(crypto).decryptWithPassword(encryptedKey, password);
			will(returnValue(null));
		}});

		storeDatabaseKey(keyFile, encryptedKeyHex);
		storeDatabaseKey(keyBackupFile, encryptedKeyHex);

		assertEquals(encryptedKeyHex, loadDatabaseKey(keyFile));
		assertEquals(encryptedKeyHex, loadDatabaseKey(keyBackupFile));

		assertFalse(accountManager.signIn(password));
		assertFalse(accountManager.hasDatabaseKey());

		assertEquals(encryptedKeyHex, loadDatabaseKey(keyFile));
		assertEquals(encryptedKeyHex, loadDatabaseKey(keyBackupFile));
	}

	@Test
	public void testSignInReturnsTrueIfPasswordIsRight() throws Exception {
		context.checking(new Expectations() {{
			oneOf(crypto).decryptWithPassword(encryptedKey, password);
			will(returnValue(key.getBytes()));
		}});

		storeDatabaseKey(keyFile, encryptedKeyHex);
		storeDatabaseKey(keyBackupFile, encryptedKeyHex);

		assertEquals(encryptedKeyHex, loadDatabaseKey(keyFile));
		assertEquals(encryptedKeyHex, loadDatabaseKey(keyBackupFile));

		assertTrue(accountManager.signIn(password));
		assertTrue(accountManager.hasDatabaseKey());
		SecretKey decrypted = accountManager.getDatabaseKey();
		assertNotNull(decrypted);
		assertArrayEquals(key.getBytes(), decrypted.getBytes());

		assertEquals(encryptedKeyHex, loadDatabaseKey(keyFile));
		assertEquals(encryptedKeyHex, loadDatabaseKey(keyBackupFile));
	}

	@Test
	public void testDbKeyIsLoadedFromPrimaryFile() throws Exception {
		storeDatabaseKey(keyFile, encryptedKeyHex);

		assertEquals(encryptedKeyHex, loadDatabaseKey(keyFile));
		assertFalse(keyBackupFile.exists());

		assertEquals(encryptedKeyHex,
				accountManager.loadEncryptedDatabaseKey());

		assertEquals(encryptedKeyHex, loadDatabaseKey(keyFile));
		assertFalse(keyBackupFile.exists());
	}

	@Test
	public void testDbKeyIsLoadedFromBackupFile() throws Exception {
		storeDatabaseKey(keyBackupFile, encryptedKeyHex);

		assertFalse(keyFile.exists());
		assertEquals(encryptedKeyHex, loadDatabaseKey(keyBackupFile));

		assertEquals(encryptedKeyHex,
				accountManager.loadEncryptedDatabaseKey());

		assertFalse(keyFile.exists());
		assertEquals(encryptedKeyHex, loadDatabaseKey(keyBackupFile));
	}

	@Test
	public void testDbKeyIsNullIfNotFound() {
		assertNull(accountManager.loadEncryptedDatabaseKey());

		assertFalse(keyFile.exists());
		assertFalse(keyBackupFile.exists());
	}

	@Test
	public void testStoringDbKeyOverwritesPrimary() throws Exception {
		storeDatabaseKey(keyFile, encryptedKeyHex);

		assertEquals(encryptedKeyHex, loadDatabaseKey(keyFile));
		assertFalse(keyBackupFile.exists());

		assertTrue(accountManager.storeEncryptedDatabaseKey(
				newEncryptedKeyHex));

		assertEquals(newEncryptedKeyHex, loadDatabaseKey(keyFile));
		assertEquals(newEncryptedKeyHex, loadDatabaseKey(keyBackupFile));
	}

	@Test
	public void testStoringDbKeyOverwritesBackup() throws Exception {
		storeDatabaseKey(keyBackupFile, encryptedKeyHex);

		assertFalse(keyFile.exists());
		assertEquals(encryptedKeyHex, loadDatabaseKey(keyBackupFile));

		assertTrue(accountManager.storeEncryptedDatabaseKey(
				newEncryptedKeyHex));

		assertEquals(newEncryptedKeyHex, loadDatabaseKey(keyFile));
		assertEquals(newEncryptedKeyHex, loadDatabaseKey(keyBackupFile));
	}

	@Test
	public void testAccountExistsReturnsFalseIfDbKeyCannotBeLoaded() {
		assertFalse(accountManager.accountExists());

		assertFalse(keyFile.exists());
		assertFalse(keyBackupFile.exists());
	}

	@Test
	public void testAccountExistsReturnsFalseIfDbDirectoryDoesNotExist()
			throws Exception {
		storeDatabaseKey(keyFile, encryptedKeyHex);
		storeDatabaseKey(keyBackupFile, encryptedKeyHex);

		assertFalse(dbDir.exists());

		assertFalse(accountManager.accountExists());

		assertEquals(encryptedKeyHex, loadDatabaseKey(keyFile));
		assertEquals(encryptedKeyHex, loadDatabaseKey(keyBackupFile));
		assertFalse(dbDir.exists());
	}

	@Test
	public void testAccountExistsReturnsFalseIfDbDirectoryIsNotDirectory()
			throws Exception {
		storeDatabaseKey(keyFile, encryptedKeyHex);
		storeDatabaseKey(keyBackupFile, encryptedKeyHex);

		assertTrue(dbDir.createNewFile());
		assertFalse(dbDir.isDirectory());

		assertFalse(accountManager.accountExists());

		assertEquals(encryptedKeyHex, loadDatabaseKey(keyFile));
		assertEquals(encryptedKeyHex, loadDatabaseKey(keyBackupFile));
		assertTrue(dbDir.exists());
		assertFalse(dbDir.isDirectory());
	}

	@Test
	public void testAccountExistsReturnsTrueIfDbDirectoryIsDirectory()
			throws Exception {
		storeDatabaseKey(keyFile, encryptedKeyHex);
		storeDatabaseKey(keyBackupFile, encryptedKeyHex);

		assertTrue(dbDir.mkdirs());
		assertTrue(dbDir.isDirectory());

		assertTrue(accountManager.accountExists());

		assertEquals(encryptedKeyHex, loadDatabaseKey(keyFile));
		assertEquals(encryptedKeyHex, loadDatabaseKey(keyBackupFile));
		assertTrue(dbDir.exists());
		assertTrue(dbDir.isDirectory());
	}

	@Test
	public void testCreateAccountStoresDbKey() throws Exception {
		context.checking(new Expectations() {{
			oneOf(identityManager).createLocalAuthor(authorName);
			will(returnValue(localAuthor));
			oneOf(identityManager).registerLocalAuthor(localAuthor);
			oneOf(crypto).generateSecretKey();
			will(returnValue(key));
			oneOf(crypto).encryptWithPassword(key.getBytes(), password);
			will(returnValue(encryptedKey));
		}});

		assertFalse(accountManager.hasDatabaseKey());

		assertTrue(accountManager.createAccount(authorName, password));

		assertTrue(accountManager.hasDatabaseKey());
		SecretKey dbKey = accountManager.getDatabaseKey();
		assertNotNull(dbKey);
		assertArrayEquals(key.getBytes(), dbKey.getBytes());

		assertEquals(encryptedKeyHex, loadDatabaseKey(keyFile));
		assertEquals(encryptedKeyHex, loadDatabaseKey(keyBackupFile));
	}

	@Test
	public void testChangePasswordReturnsFalseIfDbKeyCannotBeLoaded() {
		assertFalse(accountManager.changePassword(password, newPassword));

		assertFalse(keyFile.exists());
		assertFalse(keyBackupFile.exists());
	}

	@Test
	public void testChangePasswordReturnsFalseIfPasswordIsWrong()
			throws Exception {
		context.checking(new Expectations() {{
			oneOf(crypto).decryptWithPassword(encryptedKey, password);
			will(returnValue(null));
		}});

		storeDatabaseKey(keyFile, encryptedKeyHex);
		storeDatabaseKey(keyBackupFile, encryptedKeyHex);

		assertFalse(accountManager.changePassword(password, newPassword));

		assertEquals(encryptedKeyHex, loadDatabaseKey(keyFile));
		assertEquals(encryptedKeyHex, loadDatabaseKey(keyBackupFile));
	}

	@Test
	public void testChangePasswordReturnsTrueIfPasswordIsRight()
			throws Exception {
		context.checking(new Expectations() {{
			oneOf(crypto).decryptWithPassword(encryptedKey, password);
			will(returnValue(key.getBytes()));
			oneOf(crypto).encryptWithPassword(key.getBytes(), newPassword);
			will(returnValue(newEncryptedKey));
		}});

		storeDatabaseKey(keyFile, encryptedKeyHex);
		storeDatabaseKey(keyBackupFile, encryptedKeyHex);

		assertTrue(accountManager.changePassword(password, newPassword));

		assertEquals(newEncryptedKeyHex, loadDatabaseKey(keyFile));
		assertEquals(newEncryptedKeyHex, loadDatabaseKey(keyBackupFile));
	}

	private void storeDatabaseKey(File f, String hex) throws IOException {
		f.getParentFile().mkdirs();
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
