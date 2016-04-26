package org.briarproject.android.controller;

import android.app.Activity;

import org.briarproject.android.controller.handler.ResultHandler;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.util.StringUtils;

import java.util.concurrent.Executor;

import javax.inject.Inject;

public class PasswordControllerImp extends ConfigControllerImp
		implements PasswordController {

	@Inject
	@CryptoExecutor
	protected Executor cryptoExecutor;
	@Inject
	protected CryptoComponent crypto;
	@Inject
	protected Activity activity;

	@Inject
	public PasswordControllerImp() {

	}

	@Override
	public void validatePassword(final String password,
			final ResultHandler<Boolean> resultHandler) {
		final byte[] encrypted = getEncryptedKey();
		cryptoExecutor.execute(new Runnable() {
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

	private byte[] getEncryptedKey() {
		String hex = getEncryptedDatabaseKey();
		return hex == null ? null : StringUtils.fromHexString(hex);
	}
}
