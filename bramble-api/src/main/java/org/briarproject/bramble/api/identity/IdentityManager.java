package org.briarproject.bramble.api.identity;

import org.briarproject.bramble.api.crypto.CryptoExecutor;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface IdentityManager {

	/**
	 * Creates a local identity with the given name.
	 */
	@CryptoExecutor
	LocalAuthor createLocalAuthor(String name);

	/**
	 * Creates an account with the given name. The account includes a handshake
	 * key pair.
	 */
	@CryptoExecutor
	Account createAccount(String name);

	/**
	 * Registers the given account with the manager. This method should be
	 * called before {@link LifecycleManager#startServices(SecretKey)}. The
	 * account is stored when {@link LifecycleManager#startServices(SecretKey)}
	 * is called. The account must include a handshake key pair.
	 */
	void registerAccount(Account a);

	/**
	 * Returns the cached local identity or loads it from the database.
	 */
	LocalAuthor getLocalAuthor() throws DbException;

	/**
	 * Returns the cached local identity or loads it from the database.
	 */
	LocalAuthor getLocalAuthor(Transaction txn) throws DbException;

}
