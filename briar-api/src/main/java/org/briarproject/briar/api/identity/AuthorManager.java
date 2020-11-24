package org.briarproject.briar.api.identity;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface AuthorManager {

	/**
	 * Returns the {@link AuthorInfo} for the given author.
	 */
	AuthorInfo getAuthorInfo(AuthorId a) throws DbException;

	/**
	 * Returns the {@link AuthorInfo} for the given author.
	 */
	AuthorInfo getAuthorInfo(Transaction txn, AuthorId a) throws DbException;

	/**
	 * Returns the {@link AuthorInfo} for the {@link LocalAuthor}.
	 */
	AuthorInfo getMyAuthorInfo() throws DbException;

	/**
	 * Returns the {@link AuthorInfo} for the {@link LocalAuthor}.
	 */
	AuthorInfo getMyAuthorInfo(Transaction txn) throws DbException;
}
