package org.briarproject.briar.android.login;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.PasswordStrengthEstimator;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.ImmediateExecutor;
import org.jmock.Expectations;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.bramble.test.TestUtils.deleteTestDirectory;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.briarproject.bramble.test.TestUtils.getTestDirectory;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.briarproject.bramble.util.StringUtils.toHexString;
import static org.briarproject.briar.android.TestDatabaseKeyUtils.loadDatabaseKey;

public class SetupControllerImplTest extends BrambleMockTestCase {

	private final SharedPreferences briarPrefs =
			context.mock(SharedPreferences.class);
	private final DatabaseConfig databaseConfig =
			context.mock(DatabaseConfig.class);
	private final CryptoComponent crypto = context.mock(CryptoComponent.class);
	private final PasswordStrengthEstimator estimator =
			context.mock(PasswordStrengthEstimator.class);
	private final SetupActivity setupActivity;

	private final Executor cryptoExecutor = new ImmediateExecutor();

	private final String authorName = getRandomString(MAX_AUTHOR_NAME_LENGTH);
	private final String password = "some.strong.pass";
	private final byte[] encryptedKey = getRandomBytes(123);
	private final SecretKey key = getSecretKey();
	private final File testDir = getTestDirectory();
	private final File keyDir = new File(testDir, "key");
	private final File keyFile = new File(keyDir, "db.key");
	private final File keyBackupFile = new File(keyDir, "db.key.bak");

	public SetupControllerImplTest() {
		context.setImposteriser(ClassImposteriser.INSTANCE);
		setupActivity = context.mock(SetupActivity.class);
	}

	@Test
	@SuppressWarnings("ResultOfMethodCallIgnored")
	public void testCreateAccount() throws Exception {
		context.checking(new Expectations() {{
			// Allow the contents of the data directory to be logged
			allowing(setupActivity).getApplicationInfo();
			will(returnValue(new ApplicationInfo() {{
				dataDir = testDir.getAbsolutePath();
			}}));
			// Set the author name and password
			oneOf(setupActivity).setAuthorName(authorName);
			oneOf(setupActivity).setPassword(password);
			// Get the author name and password
			oneOf(setupActivity).getAuthorName();
			will(returnValue(authorName));
			oneOf(setupActivity).getPassword();
			will(returnValue(password));
			// Generate a database key
			oneOf(crypto).generateSecretKey();
			will(returnValue(key));
			// Attach the author name and database key to the database config
			oneOf(databaseConfig).setLocalAuthorName(authorName);
			oneOf(databaseConfig).setEncryptionKey(key);
			// Encrypt the key with the password
			oneOf(crypto).encryptWithPassword(key.getBytes(), password);
			will(returnValue(encryptedKey));
			// Store the encrypted key
			allowing(databaseConfig).getDatabaseKeyDirectory();
			will(returnValue(keyDir));
		}});

		assertFalse(keyFile.exists());
		assertFalse(keyBackupFile.exists());

		SetupControllerImpl s = new SetupControllerImpl(briarPrefs,
				databaseConfig, cryptoExecutor, crypto, estimator);
		s.setSetupActivity(setupActivity);

		AtomicBoolean called = new AtomicBoolean(false);
		s.setAuthorName(authorName);
		s.setPassword(password);
		s.createAccount(result -> called.set(true));
		assertTrue(called.get());

		assertTrue(keyFile.exists());
		assertTrue(keyBackupFile.exists());
		assertEquals(toHexString(encryptedKey), loadDatabaseKey(keyFile));
		assertEquals(toHexString(encryptedKey), loadDatabaseKey(keyBackupFile));
	}

	@After
	public void tearDown() {
		deleteTestDirectory(testDir);
	}
}
