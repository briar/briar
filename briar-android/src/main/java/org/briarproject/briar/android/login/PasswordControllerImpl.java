package org.briarproject.briar.android.login;

import android.content.SharedPreferences;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.CryptoExecutor;
import org.briarproject.bramble.api.crypto.PasswordStrengthEstimator;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.util.StringUtils;
import org.briarproject.briar.android.controller.ConfigControllerImpl;
import org.briarproject.briar.android.controller.handler.ResultHandler;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;

@NotNullByDefault
public class PasswordControllerImpl extends ConfigControllerImpl
		implements PasswordController {

	private static final Logger LOG =
			Logger.getLogger(PasswordControllerImpl.class.getName());

	protected final Executor cryptoExecutor;
	protected final CryptoComponent crypto;
	private final PasswordStrengthEstimator strengthEstimator;

	@Inject
	PasswordControllerImpl(SharedPreferences briarPrefs,
			DatabaseConfig databaseConfig,
			@CryptoExecutor Executor cryptoExecutor, CryptoComponent crypto,
			PasswordStrengthEstimator strengthEstimator) {
		super(briarPrefs, databaseConfig);
		this.cryptoExecutor = cryptoExecutor;
		this.crypto = crypto;
		this.strengthEstimator = strengthEstimator;
	}

	@Override
	public float estimatePasswordStrength(String password) {
		return strengthEstimator.estimateStrength(password);
	}

	@Override
	public void validatePassword(String password,
			ResultHandler<Boolean> resultHandler) {
		byte[] encrypted = getEncryptedKey();
		cryptoExecutor.execute(() -> {
			byte[] key = crypto.decryptWithPassword(encrypted, password);
			if (key == null) {
				resultHandler.onResult(false);
			} else {
				databaseConfig.setEncryptionKey(new SecretKey(key));
				resultHandler.onResult(true);
			}
		});
	}

	@Override
	public void changePassword(String password, String newPassword,
			ResultHandler<Boolean> resultHandler) {
		byte[] encrypted = getEncryptedKey();
		cryptoExecutor.execute(() -> {
			byte[] key = crypto.decryptWithPassword(encrypted, password);
			if (key == null) {
				resultHandler.onResult(false);
			} else {
				String hex =
						encryptDatabaseKey(new SecretKey(key), newPassword);
				storeEncryptedDatabaseKey(hex);
				resultHandler.onResult(true);
			}
		});
	}

	private byte[] getEncryptedKey() {
		String hex = getEncryptedDatabaseKey();
		if (hex == null)
			throw new IllegalStateException("Encrypted database key is null");
		return StringUtils.fromHexString(hex);
	}

	@CryptoExecutor
	String encryptDatabaseKey(SecretKey key, String password) {
		long now = System.currentTimeMillis();
		byte[] encrypted = crypto.encryptWithPassword(key.getBytes(), password);
		long duration = System.currentTimeMillis() - now;
		if (LOG.isLoggable(INFO))
			LOG.info("Key derivation took " + duration + " ms");
		return StringUtils.toHexString(encrypted);
	}
}
