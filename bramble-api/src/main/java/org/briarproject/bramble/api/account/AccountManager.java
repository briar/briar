package org.briarproject.bramble.api.account;

import org.briarproject.bramble.api.crypto.CryptoExecutor;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;

@NotNullByDefault
public interface AccountManager {

	@CryptoExecutor
	void createAccount(String name, String password);

	AccountState getAccountState();

	/**
	 * Returns the name of the {@link LocalAuthor} if it was just created and
	 * null otherwise.
	 *
	 * See {@link IdentityManager#getLocalAuthor()} for reliable retrieval.
	 */
	@Nullable
	String getCreatedLocalAuthorName();

	/**
	 * Validates the account password and returns true if it was valid.
	 */
	boolean validatePassword(String password);

	/**
	 * Changes the password and returns true if successful, false otherwise.
	 */
	@CryptoExecutor
	boolean changePassword(String password, String newPassword);

	void deleteAccount();
}
