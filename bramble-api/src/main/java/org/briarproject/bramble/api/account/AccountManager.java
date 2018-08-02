package org.briarproject.bramble.api.account;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;

@NotNullByDefault
public interface AccountManager {

	/**
	 * Returns true if the manager has the database key. This will be false
	 * before {@link #createAccount(String, String)} or {@link #signIn(String)}
	 * has been called, and true after {@link #createAccount(String, String)}
	 * or {@link #signIn(String)} has returned true, until the process exits.
	 */
	boolean hasDatabaseKey();

	/**
	 * Returns the database key if the manager has it. This will be null
	 * before {@link #createAccount(String, String)} or {@link #signIn(String)}
	 * has been called, and non-null after
	 * {@link #createAccount(String, String)} or {@link #signIn(String)} has
	 * returned true, until the process exits.
	 */
	@Nullable
	SecretKey getDatabaseKey();

	/**
	 * Returns true if the encrypted database key can be loaded from disk, and
	 * the database directory exists and is a directory.
	 */
	boolean accountExists();

	/**
	 * Creates an identity with the given name and registers it with the
	 * {@link IdentityManager}. Creates a database key, encrypts it with the
	 * given password and stores it on disk.
	 * <p/>
	 * This method does not create the database directory, so
	 * {@link #accountExists()} will continue to return false until the
	 * database directory is created.
	 */
	boolean createAccount(String name, String password);

	/**
	 * Deletes all account state from disk. {@link #accountExists()} will
	 * return false after this method returns.
	 */
	void deleteAccount();

	/**
	 * Loads the encrypted database key from disk and decrypts it with the
	 * given password.
	 *
	 * @return true if the database key was successfully loaded and decrypted.
	 */
	boolean signIn(String password);

	/**
	 * Loads the encrypted database key from disk, decrypts it with the old
	 * password, encrypts it with the new password, and stores it on disk,
	 * replacing the old key.
	 *
	 * @return true if the database key was successfully loaded, re-encrypted
	 * and stored.
	 */
	boolean changePassword(String oldPassword, String newPassword);
}
