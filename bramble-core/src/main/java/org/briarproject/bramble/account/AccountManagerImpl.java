package org.briarproject.bramble.account;


import org.briarproject.bramble.api.account.AccountManager;
import org.briarproject.bramble.api.account.AccountState;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.CryptoExecutor;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.util.StringUtils;

import java.util.logging.Logger;

import javax.annotation.Nullable;

import static java.util.logging.Level.INFO;
import static org.briarproject.bramble.api.account.AccountState.CREATING_ACCOUNT;
import static org.briarproject.bramble.api.account.AccountState.NO_ACCOUNT;
import static org.briarproject.bramble.api.account.AccountState.SIGNED_IN;
import static org.briarproject.bramble.api.account.AccountState.SIGNED_OUT;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.now;

@NotNullByDefault
public abstract class AccountManagerImpl implements AccountManager {

	private final static Logger LOG =
			Logger.getLogger(AccountManagerImpl.class.getSimpleName());

	protected final DatabaseConfig databaseConfig;
	private final CryptoComponent crypto;
	@Nullable
	private volatile String nickname = null;

	public AccountManagerImpl(CryptoComponent crypto,
			DatabaseConfig databaseConfig) {
		this.crypto = crypto;
		this.databaseConfig = databaseConfig;
	}

	protected abstract boolean storeEncryptedDatabaseKey(String hex);

	@Nullable
	protected abstract String getEncryptedDatabaseKey();

	private boolean hasEncryptedDatabaseKey() {
		return getEncryptedDatabaseKey() != null;
	}

	@Override
	@CryptoExecutor
	public void createAccount(String name, String password) {
		LOG.info("Setting local author name");
		this.nickname = name;
		SecretKey key = crypto.generateSecretKey();
		databaseConfig.setEncryptionKey(key);
		String hex = encryptDatabaseKey(key, password);
		storeEncryptedDatabaseKey(hex);
	}

	@Override
	public AccountState getAccountState() {
		AccountState state;
		if (!databaseConfig.databaseExists() && nickname != null &&
				hasEncryptedDatabaseKey()) {
			state = CREATING_ACCOUNT;
		} else if (!hasEncryptedDatabaseKey()) {
			state = NO_ACCOUNT;
		} else if (databaseConfig.getEncryptionKey() == null) {
			state = SIGNED_OUT;
		} else {
			state = SIGNED_IN;
		}
		// TODO SIGNING_IN, SIGNING_OUT, DELETING_ACCOUNT
		if (LOG.isLoggable(INFO)) LOG.info("Account State: " + state.name());
		return state;
	}

	@Nullable
	@Override
	public String getCreatedLocalAuthorName() {
		String nickname = this.nickname;
		if (LOG.isLoggable(INFO))
			LOG.info("Local author name has been set: " + (nickname != null));
		return nickname;
	}

	@CryptoExecutor
	private String encryptDatabaseKey(SecretKey key, String password) {
		long start = now();
		byte[] encrypted = crypto.encryptWithPassword(key.getBytes(), password);
		logDuration(LOG, "Key derivation", start);
		return StringUtils.toHexString(encrypted);
	}

	@Override
	public boolean validatePassword(String password) {
		byte[] encrypted = getEncryptedKeyAsBytes();
		byte[] key = crypto.decryptWithPassword(encrypted, password);
		if (key == null) {
			return false;
		}
		databaseConfig.setEncryptionKey(new SecretKey(key));
		return true;
	}

	@Override
	@CryptoExecutor
	public boolean changePassword(String password, String newPassword) {
		byte[] encrypted = getEncryptedKeyAsBytes();
		byte[] key = crypto.decryptWithPassword(encrypted, password);
		if (key == null) {
			return false;
		}
		String hex = encryptDatabaseKey(new SecretKey(key), newPassword);
		return storeEncryptedDatabaseKey(hex);
	}

	private byte[] getEncryptedKeyAsBytes() {
		String hex = getEncryptedDatabaseKey();
		if (hex == null)
			throw new IllegalStateException("Encrypted database key is null");
		return StringUtils.fromHexString(hex);
	}

}
