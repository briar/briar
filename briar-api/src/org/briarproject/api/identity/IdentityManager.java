package org.briarproject.api.identity;

import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.identity.Author.Status;

import java.util.Collection;

public interface IdentityManager {

	/** Registers a hook to be called whenever a local pseudonym is added. */
	void registerAddIdentityHook(AddIdentityHook hook);

	/** Registers a hook to be called whenever a local pseudonym is removed. */
	void registerRemoveIdentityHook(RemoveIdentityHook hook);

	/** Stores a local pseudonym. */
	void addLocalAuthor(LocalAuthor a) throws DbException;

	/** Returns the local pseudonym with the given ID. */
	LocalAuthor getLocalAuthor(AuthorId a) throws DbException;

	/** Returns the local pseudonym with the given ID. */
	LocalAuthor getLocalAuthor(Transaction txn, AuthorId a) throws DbException;

	/** Returns the main local identity. */
	LocalAuthor getLocalAuthor() throws DbException;

	/** Returns the main local identity within the given Transaction. */
	LocalAuthor getLocalAuthor(Transaction txn) throws DbException;

	/** Returns all local pseudonyms. */
	Collection<LocalAuthor> getLocalAuthors() throws DbException;

	/** Removes a local pseudonym and all associated state. */
	void removeLocalAuthor(AuthorId a) throws DbException;

	/** Returns the trust-level status of the author */
	Status getAuthorStatus(AuthorId a) throws DbException;

	/** Returns the trust-level status of the author */
	Status getAuthorStatus(Transaction txn, AuthorId a) throws DbException;

	interface AddIdentityHook {
		void addingIdentity(Transaction txn, LocalAuthor a) throws DbException;
	}

	interface RemoveIdentityHook {
		void removingIdentity(Transaction txn, LocalAuthor a)
				throws DbException;
	}
}
