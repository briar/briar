package org.briarproject.android.helper;

import android.app.Activity;
import android.content.SharedPreferences;

import org.briarproject.android.api.ReferenceManager;
import org.briarproject.android.event.AppBus;
import org.briarproject.android.event.LocalAuthorCreatedEvent;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.crypto.KeyPair;
import org.briarproject.api.crypto.PasswordStrengthEstimator;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DatabaseConfig;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.util.StringUtils;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;

public class SetupHelperImp implements SetupHelper {

	private static final Logger LOG =
			Logger.getLogger(SetupHelperImp.class.getName());

	private final static String PREF_DB_KEY = "key";

	@Inject
	@CryptoExecutor
	protected Executor cryptoExecutor;
	@Inject
	protected PasswordStrengthEstimator strengthEstimator;

	// Fields that are accessed from background threads must be volatile
	@Inject
	protected volatile CryptoComponent crypto;
	@Inject
	protected volatile DatabaseConfig databaseConfig;
	@Inject
	protected volatile AuthorFactory authorFactory;
	@Inject
	protected volatile ReferenceManager referenceManager;

	@Inject
	protected Activity activity;
	@Inject
	protected SharedPreferences briarPrefs;
	@Inject
	protected AppBus appBus;

	@Inject
	public SetupHelperImp() {

	}

	private String encryptDatabaseKey(SecretKey key, String password) {
		long now = System.currentTimeMillis();
		byte[] encrypted = crypto.encryptWithPassword(key.getBytes(), password);
		long duration = System.currentTimeMillis() - now;
		if (LOG.isLoggable(INFO))
			LOG.info("Key derivation took " + duration + " ms");
		return StringUtils.toHexString(encrypted);
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
	public void createIdentity(final String nickname, final String password) {
		cryptoExecutor.execute(new Runnable() {
			public void run() {
				SecretKey key = crypto.generateSecretKey();
				databaseConfig.setEncryptionKey(key);
				String hex = encryptDatabaseKey(key, password);
				storeEncryptedDatabaseKey(hex);
				final LocalAuthor localAuthor = createLocalAuthor(nickname);
				onIdentityCreated(referenceManager.putReference(localAuthor,
						LocalAuthor.class));

			}
		});
	}

	private void onIdentityCreated(final long handle) {
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				appBus.broadcast(new LocalAuthorCreatedEvent(handle));
			}
		});
	}

	private void storeEncryptedDatabaseKey(final String hex) {
		SharedPreferences.Editor editor = briarPrefs.edit();
		editor.putString(PREF_DB_KEY, hex);
		editor.apply();
	}

}
