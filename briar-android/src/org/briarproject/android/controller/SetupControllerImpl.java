package org.briarproject.android.controller;

import android.content.SharedPreferences;

import org.briarproject.android.api.ReferenceManager;
import org.briarproject.android.controller.handler.ResultHandler;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.crypto.KeyPair;
import org.briarproject.api.crypto.PasswordStrengthEstimator;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DatabaseConfig;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.LocalAuthor;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;

public class SetupControllerImpl extends PasswordControllerImpl
		implements SetupController {

	private static final Logger LOG =
			Logger.getLogger(SetupControllerImpl.class.getName());

	private final PasswordStrengthEstimator strengthEstimator;
	private final AuthorFactory authorFactory;
	private final ReferenceManager referenceManager;

	@Inject
	SetupControllerImpl(SharedPreferences briarPrefs,
			DatabaseConfig databaseConfig,
			@CryptoExecutor Executor cryptoExecutor, CryptoComponent crypto,
			PasswordStrengthEstimator strengthEstimator,
			AuthorFactory authorFactory, ReferenceManager referenceManager) {
		super(briarPrefs, databaseConfig, cryptoExecutor, crypto);
		this.strengthEstimator = strengthEstimator;
		this.authorFactory = authorFactory;
		this.referenceManager = referenceManager;

	}

	private LocalAuthor createLocalAuthor(String nickname) {
		long now = System.currentTimeMillis();
		KeyPair keyPair = crypto.generateSignatureKeyPair();
		byte[] publicKey = keyPair.getPublic().getEncoded();
		byte[] privateKey = keyPair.getPrivate().getEncoded();
		LocalAuthor localAuthor = authorFactory.createLocalAuthor(nickname,
				publicKey, privateKey);
		long duration = System.currentTimeMillis() - now;
		if (LOG.isLoggable(INFO))
			LOG.info("Identity creation took " + duration + " ms");
		return localAuthor;
	}

	@Override
	public float estimatePasswordStrength(String password) {
		return strengthEstimator.estimateStrength(password);
	}

	@Override
	public void createIdentity(final String nickname, final String password,
			final ResultHandler<Long> resultHandler) {
		cryptoExecutor.execute(new Runnable() {
			@Override
			public void run() {
				SecretKey key = crypto.generateSecretKey();
				databaseConfig.setEncryptionKey(key);
				String hex = encryptDatabaseKey(key, password);
				setEncryptedDatabaseKey(hex);
				LocalAuthor localAuthor = createLocalAuthor(nickname);
				long handle = referenceManager.putReference(localAuthor,
						LocalAuthor.class);
				resultHandler.onResult(handle);
			}
		});
	}
}
