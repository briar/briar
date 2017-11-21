package org.briarproject.briar.android.login;

import android.content.SharedPreferences;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.PasswordStrengthEstimator;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.ImmediateExecutor;
import org.jmock.Expectations;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static junit.framework.Assert.assertTrue;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.briarproject.bramble.util.StringUtils.getRandomString;

public class SetupControllerImplTest extends BrambleMockTestCase {

	private final SharedPreferences briarPrefs =
			context.mock(SharedPreferences.class);
	private final DatabaseConfig databaseConfig =
			context.mock(DatabaseConfig.class);
	private final CryptoComponent crypto = context.mock(CryptoComponent.class);
	private final PasswordStrengthEstimator estimator =
			context.mock(PasswordStrengthEstimator.class);
	private final SharedPreferences.Editor editor =
			context.mock(SharedPreferences.Editor.class);
	private final SetupActivity setupActivity;

	private final Executor cryptoExecutor = new ImmediateExecutor();

	private final String authorName = getRandomString(MAX_AUTHOR_NAME_LENGTH);
	private final String password = "some.strong.pass";
	private final String encryptedHex = "010203";
	private final byte[] encryptedBytes = new byte[] {1, 2, 3};
	private final SecretKey key = getSecretKey();

	public SetupControllerImplTest() {
		context.setImposteriser(ClassImposteriser.INSTANCE);
		setupActivity = context.mock(SetupActivity.class);
	}

	@Test
	public void testCreateAccount() {
		context.checking(new Expectations() {{
			// Setting the author name shows the password fragment
			oneOf(setupActivity).showPasswordFragment();
			// Generate a database key
			oneOf(crypto).generateSecretKey();
			will(returnValue(key));
			// Attach the author name and database key to the database config
			oneOf(databaseConfig).setLocalAuthorName(authorName);
			oneOf(databaseConfig).setEncryptionKey(key);
			// Encrypt the key with the password
			oneOf(crypto).encryptWithPassword(key.getBytes(), password);
			will(returnValue(encryptedBytes));
			// Store the encrypted key
			oneOf(briarPrefs).edit();
			will(returnValue(editor));
			oneOf(editor).putString("key", encryptedHex);
			oneOf(editor).apply();
		}});

		SetupControllerImpl s = new SetupControllerImpl(briarPrefs,
				databaseConfig, cryptoExecutor, crypto, estimator);
		s.setSetupActivity(setupActivity);

		AtomicBoolean called = new AtomicBoolean(false);
		s.setAuthorName(authorName);
		s.setPassword(password);
		s.createAccount(result -> called.set(true));
		assertTrue(called.get());
	}
}
