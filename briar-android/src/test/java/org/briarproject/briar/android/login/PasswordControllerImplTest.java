package org.briarproject.briar.android.login;

import android.content.SharedPreferences;

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
import static org.briarproject.bramble.test.TestUtils.getSecretKey;

public class PasswordControllerImplTest extends BrambleMockTestCase {

	private final SharedPreferences briarPrefs =
			context.mock(SharedPreferences.class);
	private final DatabaseConfig databaseConfig =
			context.mock(DatabaseConfig.class);
	private final CryptoComponent crypto = context.mock(CryptoComponent.class);
	private final PasswordStrengthEstimator estimator =
			context.mock(PasswordStrengthEstimator.class);
	private final SharedPreferences.Editor editor =
			context.mock(SharedPreferences.Editor.class);

	private final Executor cryptoExecutor = new ImmediateExecutor();

	private final String oldPassword = "some.old.pass";
	private final String newPassword = "some.new.pass";
	private final String oldEncryptedHex = "010203";
	private final String newEncryptedHex = "020304";
	private final byte[] oldEncryptedBytes = new byte[] {1, 2, 3};
	private final byte[] newEncryptedBytes = new byte[] {2, 3, 4};
	private final byte[] keyBytes = getSecretKey().getBytes();

	@Test
	public void testChangePasswordReturnsTrue() {
		context.checking(new Expectations() {{
			// Look up the encrypted DB key
			oneOf(briarPrefs).getString("key", null);
			will(returnValue(oldEncryptedHex));
			// Decrypt and re-encrypt the key
			oneOf(crypto).decryptWithPassword(oldEncryptedBytes, oldPassword);
			will(returnValue(keyBytes));
			oneOf(crypto).encryptWithPassword(keyBytes, newPassword);
			will(returnValue(newEncryptedBytes));
			// Store the re-encrypted key
			oneOf(briarPrefs).edit();
			will(returnValue(editor));
			oneOf(editor).putString("key", newEncryptedHex);
			oneOf(editor).apply();
		}});

		PasswordControllerImpl p = new PasswordControllerImpl(briarPrefs,
				databaseConfig, cryptoExecutor, crypto, estimator);

		AtomicBoolean capturedResult = new AtomicBoolean(false);
		p.changePassword(oldPassword, newPassword, capturedResult::set);
		assertTrue(capturedResult.get());
	}

	@Test
	public void testChangePasswordReturnsFalseIfOldPasswordIsWrong() {
		context.checking(new Expectations() {{
			// Look up the encrypted DB key
			oneOf(briarPrefs).getString("key", null);
			will(returnValue(oldEncryptedHex));
			// Try to decrypt the key - the password is wrong
			oneOf(crypto).decryptWithPassword(oldEncryptedBytes, oldPassword);
			will(returnValue(null));
		}});

		PasswordControllerImpl p = new PasswordControllerImpl(briarPrefs,
				databaseConfig, cryptoExecutor, crypto, estimator);

		AtomicBoolean capturedResult = new AtomicBoolean(true);
		p.changePassword(oldPassword, newPassword, capturedResult::set);
		assertFalse(capturedResult.get());
	}
}
