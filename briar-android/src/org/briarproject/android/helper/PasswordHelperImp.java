package org.briarproject.android.helper;

import android.app.Activity;

import org.briarproject.android.event.AppBus;
import org.briarproject.android.event.ErrorEvent;
import org.briarproject.android.event.PasswordValidateEvent;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.util.StringUtils;

import java.util.concurrent.Executor;

import javax.inject.Inject;

public class PasswordHelperImp extends ConfigHelperImp
		implements PasswordHelper {

	@Inject
	@CryptoExecutor
	protected Executor cryptoExecutor;
	@Inject
	protected CryptoComponent crypto;
	@Inject
	protected Activity activity;
	@Inject
	protected AppBus appBus;

	@Inject
	public PasswordHelperImp() {

	}

	@Override
	public void validatePassword(final String password) {
		final byte[] encrypted = getEncryptedKey();
		if (encrypted == null) {
			appBus.broadcast(new ErrorEvent());
		}
		cryptoExecutor.execute(new Runnable() {
			public void run() {
				byte[] key = crypto.decryptWithPassword(encrypted, password);
				if (key == null) {
//					tryAgain();.
					onPasswordValidated(false);
				} else {
					databaseConfig.setEncryptionKey(new SecretKey(key));
					onPasswordValidated(true);
//					setResultAndFinish();
				}
			}
		});
	}

	private void onPasswordValidated(final boolean validated) {
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				appBus.broadcast(new PasswordValidateEvent(validated));
			}
		});
	}


	private byte[] getEncryptedKey() {
		String hex = getEncryptedDatabaseKey();
		return hex == null? null : StringUtils.fromHexString(hex);
	}
}
