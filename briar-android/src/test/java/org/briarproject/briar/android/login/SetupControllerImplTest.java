package org.briarproject.briar.android.login;

import org.briarproject.bramble.api.account.AccountManager;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.PasswordStrengthEstimator;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.ImmediateExecutor;
import org.jmock.Expectations;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static junit.framework.Assert.assertTrue;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.bramble.test.TestUtils.getLocalAuthor;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.briarproject.bramble.util.StringUtils.toHexString;

public class SetupControllerImplTest extends BrambleMockTestCase {

	private final AccountManager accountManager =
			context.mock(AccountManager.class);
	private final CryptoComponent crypto = context.mock(CryptoComponent.class);
	private final PasswordStrengthEstimator estimator =
			context.mock(PasswordStrengthEstimator.class);
	private final IdentityManager identityManager =
			context.mock(IdentityManager.class);
	private final SetupActivity setupActivity;

	private final Executor cryptoExecutor = new ImmediateExecutor();

	private final String authorName = getRandomString(MAX_AUTHOR_NAME_LENGTH);
	private final String password = "some.strong.pass";
	private final LocalAuthor localAuthor = getLocalAuthor();
	private final byte[] encryptedKey = getRandomBytes(123);
	private final String encryptedKeyHex = toHexString(encryptedKey);
	private final SecretKey key = getSecretKey();

	public SetupControllerImplTest() {
		context.setImposteriser(ClassImposteriser.INSTANCE);
		setupActivity = context.mock(SetupActivity.class);
	}

	@Test
	@SuppressWarnings("ResultOfMethodCallIgnored")
	public void testCreateAccount() {
		context.checking(new Expectations() {{
			// Set the author name and password
			oneOf(setupActivity).setAuthorName(authorName);
			oneOf(setupActivity).setPassword(password);
			// Get the author name and password
			oneOf(setupActivity).getAuthorName();
			will(returnValue(authorName));
			oneOf(setupActivity).getPassword();
			will(returnValue(password));
			// Create and register the local author
			oneOf(identityManager).createLocalAuthor(authorName);
			will(returnValue(localAuthor));
			oneOf(identityManager).registerLocalAuthor(localAuthor);
			// Generate a database key
			oneOf(crypto).generateSecretKey();
			will(returnValue(key));
			// Encrypt the key with the password
			oneOf(crypto).encryptWithPassword(key.getBytes(), password);
			will(returnValue(encryptedKey));
			// Store the encrypted key
			oneOf(accountManager).storeEncryptedDatabaseKey(encryptedKeyHex);
			will(returnValue(true));
			// Pass the database key to the account manager
			oneOf(accountManager).setDatabaseKey(key);
		}});

		SetupControllerImpl s = new SetupControllerImpl(accountManager,
				cryptoExecutor, crypto, estimator, identityManager);
		s.setSetupActivity(setupActivity);

		AtomicBoolean called = new AtomicBoolean(false);
		s.setAuthorName(authorName);
		s.setPassword(password);
		s.createAccount(result -> called.set(true));
		assertTrue(called.get());
	}
}
