package org.briarproject.briar.android.login;

import org.briarproject.bramble.api.account.AccountManager;
import org.briarproject.bramble.api.crypto.PasswordStrengthEstimator;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.ImmediateExecutor;
import org.jmock.Expectations;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.briarproject.bramble.util.StringUtils.getRandomString;

public class PasswordControllerImplTest extends BrambleMockTestCase {

	private final AccountManager accountManager =
			context.mock(AccountManager.class);
	private final PasswordStrengthEstimator estimator =
			context.mock(PasswordStrengthEstimator.class);

	private final Executor ioExecutor = new ImmediateExecutor();

	private final String oldPassword = getRandomString(10);
	private final String newPassword = getRandomString(10);

	@Test
	public void testChangePasswordReturnsTrue() {
		context.checking(new Expectations() {{
			oneOf(accountManager).changePassword(oldPassword, newPassword);
			will(returnValue(true));
		}});

		PasswordControllerImpl p = new PasswordControllerImpl(accountManager,
				ioExecutor, estimator);

		AtomicBoolean capturedResult = new AtomicBoolean(false);
		p.changePassword(oldPassword, newPassword, capturedResult::set);
		assertTrue(capturedResult.get());
	}

	@Test
	public void testChangePasswordReturnsFalseIfOldPasswordIsWrong() {
		context.checking(new Expectations() {{
			oneOf(accountManager).changePassword(oldPassword, newPassword);
			will(returnValue(false));
		}});

		PasswordControllerImpl p = new PasswordControllerImpl(accountManager,
				ioExecutor, estimator);

		AtomicBoolean capturedResult = new AtomicBoolean(true);
		p.changePassword(oldPassword, newPassword, capturedResult::set);
		assertFalse(capturedResult.get());
	}
}
