package org.briarproject.briar.android.login;

import android.content.SharedPreferences;

import org.briarproject.bramble.api.account.AccountManager;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.PasswordStrengthEstimator;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.ImmediateExecutor;
import org.jmock.Expectations;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.briarproject.bramble.util.StringUtils.toHexString;

public class PasswordControllerImplTest extends BrambleMockTestCase {

	private final SharedPreferences briarPrefs =
			context.mock(SharedPreferences.class);
	private final AccountManager accountManager =
			context.mock(AccountManager.class);
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
	private final String oldEncryptedKeyHex = toHexString(oldEncryptedKey);
	private final String newEncryptedKeyHex = toHexString(newEncryptedKey);

	@Test
	public void testChangePasswordReturnsTrue() {
		context.checking(new Expectations() {{
			// Look up the encrypted DB key
			oneOf(briarPrefs).getString("key", null);
			will(returnValue(null));
			oneOf(accountManager).getEncryptedDatabaseKey();
			will(returnValue(oldEncryptedKeyHex));
			// Decrypt and re-encrypt the key
			oneOf(crypto).decryptWithPassword(oldEncryptedKey, oldPassword);
			will(returnValue(key));
			oneOf(crypto).encryptWithPassword(key, newPassword);
			will(returnValue(newEncryptedKey));
			// Store the new key
			oneOf(accountManager).storeEncryptedDatabaseKey(newEncryptedKeyHex);
			will(returnValue(true));
		}});

		PasswordControllerImpl p = new PasswordControllerImpl(briarPrefs,
				accountManager, databaseConfig, cryptoExecutor, crypto,
				estimator);

		AtomicBoolean capturedResult = new AtomicBoolean(false);
		p.changePassword(oldPassword, newPassword, capturedResult::set);
		assertTrue(capturedResult.get());
	}

	@Test
	public void testChangePasswordReturnsFalseIfOldPasswordIsWrong() {
		context.checking(new Expectations() {{
			// Look up the encrypted DB key
			oneOf(briarPrefs).getString("key", null);
			will(returnValue(null));
			oneOf(accountManager).getEncryptedDatabaseKey();
			will(returnValue(oldEncryptedKeyHex));
			// Try to decrypt the key - the password is wrong
			oneOf(crypto).decryptWithPassword(oldEncryptedKey, oldPassword);
			will(returnValue(null));
		}});

		PasswordControllerImpl p = new PasswordControllerImpl(briarPrefs,
				accountManager, databaseConfig, cryptoExecutor, crypto,
				estimator);

		AtomicBoolean capturedResult = new AtomicBoolean(true);
		p.changePassword(oldPassword, newPassword, capturedResult::set);
		assertFalse(capturedResult.get());
	}
}
