package org.briarproject.briar.android.login;

import org.briarproject.bramble.api.account.AccountManager;
import org.briarproject.bramble.api.crypto.PasswordStrengthEstimator;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.android.controller.handler.ResultHandler;

import java.util.concurrent.Executor;

import javax.inject.Inject;

@NotNullByDefault
public class PasswordControllerImpl implements PasswordController {

	protected final AccountManager accountManager;
	protected final Executor ioExecutor;
	private final PasswordStrengthEstimator strengthEstimator;

	@Inject
	PasswordControllerImpl(AccountManager accountManager,
			@IoExecutor Executor ioExecutor,
			PasswordStrengthEstimator strengthEstimator) {
		this.accountManager = accountManager;
		this.ioExecutor = ioExecutor;
		this.strengthEstimator = strengthEstimator;
	}

	@Override
	public float estimatePasswordStrength(String password) {
		return strengthEstimator.estimateStrength(password);
	}

	@Override
	public void validatePassword(String password,
			ResultHandler<Boolean> resultHandler) {
		ioExecutor.execute(() ->
				resultHandler.onResult(accountManager.signIn(password)));
	}

	@Override
	public void changePassword(String oldPassword, String newPassword,
			ResultHandler<Boolean> resultHandler) {
		ioExecutor.execute(() -> {
			boolean changed =
					accountManager.changePassword(oldPassword, newPassword);
			resultHandler.onResult(changed);
		});
	}
}
