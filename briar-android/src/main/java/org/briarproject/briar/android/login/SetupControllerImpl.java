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
	private String authorName, password;
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
		this.authorName = authorName;
		if (setupActivity == null) throw new IllegalStateException();
		setupActivity.showPasswordFragment();
	}

	@Override
	public void setPassword(String password) {
		this.password = password;
	}

	@Override
	public void showDozeOrCreateAccount() {
		if (setupActivity == null) throw new IllegalStateException();
		if (needToShowDozeFragment()) {
			setupActivity.showDozeFragment();
		} else {
			createAccount();
		}
	}

	@Override
	public void createAccount() {
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

	@Override
	public void createAccount(ResultHandler<Void> resultHandler) {
		if (authorName == null || password == null)
			throw new IllegalStateException();
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
