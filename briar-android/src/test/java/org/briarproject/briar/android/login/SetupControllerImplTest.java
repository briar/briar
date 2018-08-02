package org.briarproject.briar.android.login;

import org.briarproject.bramble.api.account.AccountManager;
import org.briarproject.bramble.api.crypto.PasswordStrengthEstimator;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.ImmediateExecutor;
import org.jmock.Expectations;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static junit.framework.Assert.assertTrue;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.bramble.util.StringUtils.getRandomString;

public class SetupControllerImplTest extends BrambleMockTestCase {

	private final AccountManager accountManager =
			context.mock(AccountManager.class);
	private final PasswordStrengthEstimator estimator =
			context.mock(PasswordStrengthEstimator.class);
	private final SetupActivity setupActivity;

	private final Executor ioExecutor = new ImmediateExecutor();

	private final String authorName = getRandomString(MAX_AUTHOR_NAME_LENGTH);
	private final String password = getRandomString(10);

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
			// Create the account
			oneOf(accountManager).createAccount(authorName, password);
			will(returnValue(true));
		}});

		SetupControllerImpl s = new SetupControllerImpl(accountManager,
				ioExecutor, estimator);
		s.setSetupActivity(setupActivity);

		AtomicBoolean called = new AtomicBoolean(false);
		s.setAuthorName(authorName);
		s.setPassword(password);
		s.createAccount(result -> called.set(true));
		assertTrue(called.get());
	}
}
