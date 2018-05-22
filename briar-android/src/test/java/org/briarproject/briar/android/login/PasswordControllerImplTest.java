package org.briarproject.briar.android.login;

import android.content.SharedPreferences;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.PasswordStrengthEstimator;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.ImmediateExecutor;
import org.jmock.Expectations;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.briarproject.bramble.test.TestUtils.deleteTestDirectory;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.briarproject.bramble.test.TestUtils.getTestDirectory;
import static org.briarproject.bramble.util.StringUtils.toHexString;
import static org.briarproject.briar.android.TestDatabaseKeyUtils.loadDatabaseKey;
import static org.briarproject.briar.android.TestDatabaseKeyUtils.storeDatabaseKey;

public class PasswordControllerImplTest extends BrambleMockTestCase {

	private final SharedPreferences briarPrefs =
			context.mock(SharedPreferences.class);
	private final DatabaseConfig databaseConfig =
			context.mock(DatabaseConfig.class);
	private final CryptoComponent crypto = context.mock(CryptoComponent.class);
	private final PasswordStrengthEstimator estimator =
			context.mock(PasswordStrengthEstimator.class);

	private final Executor cryptoExecutor = new ImmediateExecutor();

	private final String oldPassword = "some.old.pass";
	private final String newPassword = "some.new.pass";
	private final byte[] oldEncryptedKey = getRandomBytes(123);
	private final byte[] newEncryptedKey = getRandomBytes(123);
	private final byte[] key = getSecretKey().getBytes();
	private final File testDir = getTestDirectory();
	private final File keyDir = new File(testDir, "key");
	private final File keyFile = new File(keyDir, "db.key");
	private final File keyBackupFile = new File(keyDir, "db.key.bak");

	@Test
	public void testChangePasswordReturnsTrue() throws Exception {
		context.checking(new Expectations() {{
			// Look up the encrypted DB key
			oneOf(briarPrefs).getString("key", null);
			will(returnValue(null));
			allowing(databaseConfig).getDatabaseKeyDirectory();
			will(returnValue(keyDir));
			// Decrypt and re-encrypt the key
			oneOf(crypto).decryptWithPassword(oldEncryptedKey, oldPassword);
			will(returnValue(key));
			oneOf(crypto).encryptWithPassword(key, newPassword);
			will(returnValue(newEncryptedKey));
		}});

		assertFalse(keyFile.exists());
		assertFalse(keyBackupFile.exists());

		storeDatabaseKey(keyFile, toHexString(oldEncryptedKey));
		storeDatabaseKey(keyBackupFile, toHexString(oldEncryptedKey));

		PasswordControllerImpl p = new PasswordControllerImpl(briarPrefs,
				databaseConfig, cryptoExecutor, crypto, estimator);

		AtomicBoolean capturedResult = new AtomicBoolean(false);
		p.changePassword(oldPassword, newPassword, capturedResult::set);
		assertTrue(capturedResult.get());

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
			oneOf(briarPrefs).getString("key", null);
			will(returnValue(null));
			allowing(databaseConfig).getDatabaseKeyDirectory();
			will(returnValue(keyDir));
			// Try to decrypt the key - the password is wrong
			oneOf(crypto).decryptWithPassword(oldEncryptedKey, oldPassword);
			will(returnValue(null));
		}});

		assertFalse(keyFile.exists());
		assertFalse(keyBackupFile.exists());

		storeDatabaseKey(keyFile, toHexString(oldEncryptedKey));
		storeDatabaseKey(keyBackupFile, toHexString(oldEncryptedKey));

		PasswordControllerImpl p = new PasswordControllerImpl(briarPrefs,
				databaseConfig, cryptoExecutor, crypto, estimator);

		AtomicBoolean capturedResult = new AtomicBoolean(true);
		p.changePassword(oldPassword, newPassword, capturedResult::set);
		assertFalse(capturedResult.get());

		assertTrue(keyFile.exists());
		assertTrue(keyBackupFile.exists());
		assertEquals(toHexString(oldEncryptedKey), loadDatabaseKey(keyFile));
		assertEquals(toHexString(oldEncryptedKey),
				loadDatabaseKey(keyBackupFile));
	}

	@After
	public void tearDown() {
		deleteTestDirectory(testDir);
	}
}
