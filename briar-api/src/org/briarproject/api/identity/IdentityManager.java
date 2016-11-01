package org.briarproject.api.identity;

import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.identity.Author.Status;

public interface IdentityManager {

	/** Stores the local pseudonym. */
	void registerLocalAuthor(LocalAuthor a) throws DbException;

	/** Returns the cached main local identity, non-blocking, or loads it from
	 * the db, blocking*/
	LocalAuthor getLocalAuthor() throws DbException;

	/** Returns the cached main local identity, non-blocking, or loads it from
	 * the db, blocking, within the given Transaction. */
	LocalAuthor getLocalAuthor(Transaction txn) throws DbException;

	/** Returns the trust-level status of the author */
	Status getAuthorStatus(AuthorId a) throws DbException;

	/** Returns the trust-level status of the author */
	Status getAuthorStatus(Transaction txn, AuthorId a) throws DbException;

}
