package org.briarproject.briar.android.login;

import android.content.SharedPreferences;
import android.support.annotation.Nullable;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.CryptoExecutor;
import org.briarproject.bramble.api.crypto.PasswordStrengthEstimator;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.android.controller.handler.ResultHandler;
import org.briarproject.briar.android.controller.handler.UiResultHandler;

import java.util.concurrent.Executor;

import javax.inject.Inject;

@NotNullByDefault
public class SetupControllerImpl extends PasswordControllerImpl
		implements SetupController {

	@Nullable
	private SetupActivity setupActivity;

	@Inject
	SetupControllerImpl(SharedPreferences briarPrefs,
			DatabaseConfig databaseConfig,
			@CryptoExecutor Executor cryptoExecutor, CryptoComponent crypto,
			PasswordStrengthEstimator strengthEstimator) {
		super(briarPrefs, databaseConfig, cryptoExecutor, crypto,
				strengthEstimator);
	}

	@Override
	public void setSetupActivity(SetupActivity setupActivity) {
		this.setupActivity = setupActivity;
	}

	@Override
	public boolean needToShowDozeFragment() {
		if (setupActivity == null) throw new IllegalStateException();
		return DozeView.needsToBeShown(setupActivity) ||
				HuaweiView.needsToBeShown(setupActivity);
	}

	@Override
	public void setAuthorName(String authorName) {
		if (setupActivity == null) throw new IllegalStateException();
		setupActivity.setAuthorName(authorName);
	}

	@Override
	public void setPassword(String password) {
		if (setupActivity == null) throw new IllegalStateException();
		setupActivity.setPassword(password);
	}

	@Override
	public void showPasswordFragment() {
		if (setupActivity == null) throw new IllegalStateException();
		setupActivity.showPasswordFragment();
	}

	@Override
	public void showDozeFragment() {
		if (setupActivity == null) throw new IllegalStateException();
		setupActivity.showDozeFragment();
	}

	@Override
	public void createAccount() {
		if (setupActivity == null) throw new IllegalStateException();
		UiResultHandler<Void> resultHandler =
				new UiResultHandler<Void>(setupActivity) {
					@Override
					public void onResultUi(Void result) {
						if (setupActivity == null)
							throw new IllegalStateException();
						setupActivity.showApp();
					}
				};
		createAccount(resultHandler);
	}

	// Package access for testing
	void createAccount(ResultHandler<Void> resultHandler) {
		if (setupActivity == null) throw new IllegalStateException();
		String authorName = setupActivity.getAuthorName();
		if (authorName == null) throw new IllegalStateException();
		String password = setupActivity.getPassword();
		if (password == null) throw new IllegalStateException();
		cryptoExecutor.execute(() -> {
			databaseConfig.setLocalAuthorName(authorName);
			SecretKey key = crypto.generateSecretKey();
			databaseConfig.setEncryptionKey(key);
			String hex = encryptDatabaseKey(key, password);
			storeEncryptedDatabaseKey(hex);
			resultHandler.onResult(null);
		});
	}

}
