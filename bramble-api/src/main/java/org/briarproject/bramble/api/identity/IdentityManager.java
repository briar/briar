package org.briarproject.bramble.api.identity;

import org.briarproject.bramble.api.crypto.CryptoExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author.Status;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface IdentityManager {

	/**
	 * Creates a local identity with the given name.
	 */
	@CryptoExecutor
	LocalAuthor createLocalAuthor(String name);

	/**
	 * Registers the given local identity with the manager. The identity is
	 * not stored until {@link #storeLocalAuthor()} is called.
	 */
	void registerLocalAuthor(LocalAuthor a);

	/**
	 * Stores the local identity registered with
	 * {@link #registerLocalAuthor(LocalAuthor)}, if any.
	 */
	void storeLocalAuthor() throws DbException;

	/**
	 * Returns the cached local identity or loads it from the database.
	 */
	LocalAuthor getLocalAuthor() throws DbException;

	/**
	 * Returns the cached local identity or loads it from the database.
	 */
	LocalAuthor getLocalAuthor(Transaction txn) throws DbException;

	/**
	 * Returns the {@link Status} of the given author.
	 */
	Status getAuthorStatus(AuthorId a) throws DbException;

	/**
	 * Returns the {@link Status} of the given author.
	 */
	Status getAuthorStatus(Transaction txn, AuthorId a) throws DbException;

}
