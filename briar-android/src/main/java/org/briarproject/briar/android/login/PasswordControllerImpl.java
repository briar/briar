package org.briarproject.briar.android.login;

import org.briarproject.bramble.api.account.AccountManager;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.CryptoExecutor;
import org.briarproject.bramble.api.crypto.PasswordStrengthEstimator;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.android.controller.handler.ResultHandler;

import java.util.concurrent.Executor;

import javax.inject.Inject;

import static org.briarproject.bramble.api.account.AccountState.NO_ACCOUNT;

@NotNullByDefault
public class PasswordControllerImpl implements PasswordController {

	protected final AccountManager accountManager;
	protected final Executor cryptoExecutor;
	protected final CryptoComponent crypto;
	private final PasswordStrengthEstimator strengthEstimator;

	@Inject
	public PasswordControllerImpl(AccountManager accountManager,
			@CryptoExecutor Executor cryptoExecutor, CryptoComponent crypto,
			PasswordStrengthEstimator strengthEstimator) {
		this.accountManager = accountManager;
		this.cryptoExecutor = cryptoExecutor;
		this.crypto = crypto;
		this.strengthEstimator = strengthEstimator;
	}

	@Override
	public boolean accountExists() {
		return accountManager.getAccountState() != NO_ACCOUNT;
	}

	@Override
	public float estimatePasswordStrength(String password) {
		return strengthEstimator.estimateStrength(password);
	}

	@Override
	public void validatePassword(String password,
			ResultHandler<Boolean> resultHandler) {
		cryptoExecutor.execute(() -> {
			boolean result = accountManager.validatePassword(password);
			resultHandler.onResult(result);
		});
	}

	@Override
	public void changePassword(String password, String newPassword,
			ResultHandler<Boolean> resultHandler) {
		cryptoExecutor.execute(() -> {
			boolean result =
					accountManager.changePassword(password, newPassword);
			resultHandler.onResult(result);
		});
	}

	@Override
	public void deleteAccount() {
		accountManager.deleteAccount();
	}

}
