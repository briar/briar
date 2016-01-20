package org.briarproject.api.identity;

import org.briarproject.api.db.DbException;

import java.util.Collection;

public interface IdentityManager {

	/** Registers a hook to be called whenever a local pseudonym is added. */
	void registerIdentityAddedHook(IdentityAddedHook hook);

	/** Registers a hook to be called whenever a local pseudonym is removed. */
	void registerIdentityRemovedHook(IdentityRemovedHook hook);

	/** Stores a local pseudonym. */
	void addLocalAuthor(LocalAuthor a) throws DbException;

	/** Returns the local pseudonym with the given ID. */
	LocalAuthor getLocalAuthor(AuthorId a) throws DbException;

	/** Returns all local pseudonyms. */
	Collection<LocalAuthor> getLocalAuthors() throws DbException;

	/** Removes a local pseudonym and all associated state. */
	void removeLocalAuthor(AuthorId a) throws DbException;

	interface IdentityAddedHook {
		void identityAdded(AuthorId a);
	}

	interface IdentityRemovedHook {
		void identityRemoved(AuthorId a);
	}
}
