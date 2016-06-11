package org.briarproject.android.controller;

import android.app.Activity;
import android.content.SharedPreferences;

import org.briarproject.android.controller.handler.ResultHandler;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.util.StringUtils;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;

public class PasswordControllerImpl extends ConfigControllerImpl
		implements PasswordController {

	private static final Logger LOG =
			Logger.getLogger(PasswordControllerImpl.class.getName());

	private final static String PREF_DB_KEY = "key";

	@Inject
	@CryptoExecutor
	protected Executor cryptoExecutor;
	@Inject
	protected Activity activity;

	// Fields that are accessed from background threads must be volatile
	@Inject
	protected CryptoComponent crypto;

	@Inject
	public PasswordControllerImpl() {

	}

	@Override
	public void validatePassword(final String password,
			final ResultHandler<Boolean> resultHandler) {
		final byte[] encrypted = getEncryptedKey();
		cryptoExecutor.execute(new Runnable() {
			@Override
			public void run() {
				byte[] key = crypto.decryptWithPassword(encrypted, password);
				if (key == null) {
					resultHandler.onResult(false);
				} else {
					databaseConfig.setEncryptionKey(new SecretKey(key));
					resultHandler.onResult(true);
				}
			}
		});
	}

	@Override
	public void changePassword(final String password, final String newPassword,
			final ResultHandler<Boolean> resultHandler) {
		final byte[] encrypted = getEncryptedKey();
		cryptoExecutor.execute(new Runnable() {
			@Override
			public void run() {
				byte[] key = crypto.decryptWithPassword(encrypted, password);
				if (key == null) {
					resultHandler.onResult(false);
				} else {
					String hex =
							encryptDatabaseKey(new SecretKey(key), newPassword);
					storeEncryptedDatabaseKey(hex);
					resultHandler.onResult(true);
				}
			}
		});
	}

	private byte[] getEncryptedKey() {
		String hex = getEncryptedDatabaseKey();
		if (hex == null)
			throw new IllegalStateException("Encrypted database key is null");
		return StringUtils.fromHexString(hex);
	}

	// Call inside cryptoExecutor
	String encryptDatabaseKey(SecretKey key, String password) {
		long now = System.currentTimeMillis();
		byte[] encrypted = crypto.encryptWithPassword(key.getBytes(), password);
		long duration = System.currentTimeMillis() - now;
		if (LOG.isLoggable(INFO))
			LOG.info("Key derivation took " + duration + " ms");
		return StringUtils.toHexString(encrypted);
	}

	void storeEncryptedDatabaseKey(String hex) {
		SharedPreferences.Editor editor = briarPrefs.edit();
		editor.putString(PREF_DB_KEY, hex);
		editor.apply();
	}
}
