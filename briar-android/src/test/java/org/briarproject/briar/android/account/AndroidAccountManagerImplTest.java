package org.briarproject.briar.android.account;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.jmock.Expectations;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.bramble.test.TestUtils.deleteTestDirectory;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.briarproject.bramble.test.TestUtils.getTestDirectory;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.briarproject.bramble.util.StringUtils.toHexString;
import static org.briarproject.briar.android.TestDatabaseKeyUtils.loadDatabaseKey;
import static org.briarproject.briar.android.TestDatabaseKeyUtils.storeDatabaseKey;
import static org.hamcrest.Matchers.samePropertyValuesAs;

public class AndroidAccountManagerImplTest extends BrambleMockTestCase {

	private final CryptoComponent cryptoComponent =
			context.mock(CryptoComponent.class);
	private final SharedPreferences prefs =
			context.mock(SharedPreferences.class);
	private final DatabaseConfig databaseConfig =
			context.mock(DatabaseConfig.class);
	private final Editor editor = context.mock(Editor.class);

	private final String authorName = getRandomString(MAX_AUTHOR_NAME_LENGTH);
	private final String password = "some.strong.pass";
	private final String oldPassword = "some.old.pass";
	private final String newPassword = "some.new.pass";
	private final SecretKey key = getSecretKey();
	private final byte[] keyBytes = key.getBytes();
	private final byte[] encryptedKey = getRandomBytes(123);
	private final String encryptedKeyHex = toHexString(encryptedKey);
	private final String oldEncryptedKeyHex = toHexString(getRandomBytes(123));
	private final byte[] oldEncryptedKey = getRandomBytes(123);
	private final byte[] newEncryptedKey = getRandomBytes(123);
	private final File testDir = getTestDirectory();
	private final File keyDir = new File(testDir, "key");
	private final File keyFile = new File(keyDir, "db.key");
	private final File keyBackupFile = new File(keyDir, "db.key.bak");

	@Test
	public void createAccount() throws IOException {
		context.checking(new Expectations() {{
			// Generate a database key
			oneOf(cryptoComponent).generateSecretKey();
			will(returnValue(key));
			// Attach the author name and database key to the database config
			oneOf(databaseConfig).setEncryptionKey(key);
			// Encrypt the key with the password
			oneOf(cryptoComponent)
					.encryptWithPassword(key.getBytes(), password);
			will(returnValue(encryptedKey));
			// Store the encrypted key
			allowing(databaseConfig).getDatabaseKeyDirectory();
			will(returnValue(keyDir));
		}});
		getAndroidAccountManagerImpl().createAccount(authorName, password);

		assertTrue(keyFile.exists());
		assertTrue(keyBackupFile.exists());
		assertEquals(toHexString(encryptedKey), loadDatabaseKey(keyFile));
		assertEquals(toHexString(encryptedKey), loadDatabaseKey(keyBackupFile));
	}

	@Test
	public void testChangePasswordReturnsTrue() throws Exception {
		context.checking(new Expectations() {{
			// Look up the encrypted DB key
			oneOf(prefs).getString("key", null);
			will(returnValue(null));
			allowing(databaseConfig).getDatabaseKeyDirectory();
			will(returnValue(keyDir));
			// Decrypt and re-encrypt the key
			oneOf(cryptoComponent)
					.decryptWithPassword(oldEncryptedKey, oldPassword);
			will(returnValue(key.getBytes()));
			oneOf(cryptoComponent)
					.encryptWithPassword(key.getBytes(), newPassword);
			will(returnValue(newEncryptedKey));
		}});

		assertFalse(keyFile.exists());
		assertFalse(keyBackupFile.exists());

		storeDatabaseKey(keyFile, toHexString(oldEncryptedKey));
		storeDatabaseKey(keyBackupFile, toHexString(oldEncryptedKey));

		AndroidAccountManagerImpl accountManager =
				getAndroidAccountManagerImpl();
		assertTrue(accountManager.changePassword(oldPassword, newPassword));

		assertTrue(keyFile.exists());
		assertTrue(keyBackupFile.exists());
		assertEquals(toHexString(newEncryptedKey), loadDatabaseKey(keyFile));
		assertEquals(toHexString(newEncryptedKey),
				loadDatabaseKey(keyBackupFile));
	}

	@Test
	public void testChangePasswordReturnsFalseIfOldPasswordIsWrong()
			throws Exception {
		context.checking(new Expectations() {{
			// Look up the encrypted DB key
			oneOf(prefs).getString("key", null);
			will(returnValue(null));
			allowing(databaseConfig).getDatabaseKeyDirectory();
			will(returnValue(keyDir));
			// Try to decrypt the key - the password is wrong
			oneOf(cryptoComponent)
					.decryptWithPassword(oldEncryptedKey, oldPassword);
			will(returnValue(null));
		}});

		assertFalse(keyFile.exists());
		assertFalse(keyBackupFile.exists());

		storeDatabaseKey(keyFile, toHexString(oldEncryptedKey));
		storeDatabaseKey(keyBackupFile, toHexString(oldEncryptedKey));

		AndroidAccountManagerImpl accountManager =
				getAndroidAccountManagerImpl();
		assertFalse(accountManager.changePassword(oldPassword, newPassword));

		assertTrue(keyFile.exists());
		assertTrue(keyBackupFile.exists());
		assertEquals(toHexString(oldEncryptedKey), loadDatabaseKey(keyFile));
		assertEquals(toHexString(oldEncryptedKey),
				loadDatabaseKey(keyBackupFile));
	}

	@Test
	public void testValidatePasswordReturnsTrue() throws Exception {
		context.checking(new Expectations() {{
			// Look up the encrypted DB key
			oneOf(prefs).getString("key", null);
			will(returnValue(null));
			allowing(databaseConfig).getDatabaseKeyDirectory();
			will(returnValue(keyDir));
			// Decrypt the key
			oneOf(cryptoComponent)
					.decryptWithPassword(encryptedKey, password);
			will(returnValue(keyBytes));
			oneOf(databaseConfig)
					.setEncryptionKey(with(samePropertyValuesAs(key)));
		}});

		assertFalse(keyFile.exists());
		assertFalse(keyBackupFile.exists());

		storeDatabaseKey(keyFile, toHexString(encryptedKey));
		storeDatabaseKey(keyBackupFile, toHexString(encryptedKey));

		AndroidAccountManagerImpl accountManager =
				getAndroidAccountManagerImpl();
		assertTrue(accountManager.validatePassword(password));
	}

	@Test
	public void testValidatePasswordReturnsFalseIfPasswordIsWrong()
			throws Exception {
		context.checking(new Expectations() {{
			// Look up the encrypted DB key
			oneOf(prefs).getString("key", null);
			will(returnValue(null));
			allowing(databaseConfig).getDatabaseKeyDirectory();
			will(returnValue(keyDir));
			// Decrypt the key
			oneOf(cryptoComponent)
					.decryptWithPassword(encryptedKey, password);
			will(returnValue(null));
		}});

		assertFalse(keyFile.exists());
		assertFalse(keyBackupFile.exists());

		storeDatabaseKey(keyFile, toHexString(encryptedKey));
		storeDatabaseKey(keyBackupFile, toHexString(encryptedKey));

		AndroidAccountManagerImpl accountManager =
				getAndroidAccountManagerImpl();
		assertFalse(accountManager.validatePassword(password));
	}

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

		AndroidAccountManagerImpl c = getAndroidAccountManagerImpl();

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

		AndroidAccountManagerImpl c = getAndroidAccountManagerImpl();

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

		AndroidAccountManagerImpl c = getAndroidAccountManagerImpl();

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

		AndroidAccountManagerImpl c = getAndroidAccountManagerImpl();

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

		AndroidAccountManagerImpl c = getAndroidAccountManagerImpl();

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

		AndroidAccountManagerImpl c = getAndroidAccountManagerImpl();

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

	private AndroidAccountManagerImpl getAndroidAccountManagerImpl() {
		// app is only needed for deleting account
		Application app = null;
		//noinspection ConstantConditions
		return new AndroidAccountManagerImpl(cryptoComponent, databaseConfig,
				app, prefs);
	}
}
