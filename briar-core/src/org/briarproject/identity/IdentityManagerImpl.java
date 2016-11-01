package org.briarproject.identity;

import org.briarproject.api.contact.Contact;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.identity.Author.Status;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;

import java.util.Collection;
import java.util.logging.Logger;

import javax.inject.Inject;

import static org.briarproject.api.identity.Author.Status.OURSELVES;
import static org.briarproject.api.identity.Author.Status.UNKNOWN;
import static org.briarproject.api.identity.Author.Status.UNVERIFIED;
import static org.briarproject.api.identity.Author.Status.VERIFIED;

class IdentityManagerImpl implements IdentityManager {
	private final DatabaseComponent db;

	private static final Logger LOG =
			Logger.getLogger(IdentityManagerImpl.class.getName());

	// The local author is immutable so we can cache it
	private volatile LocalAuthor cachedAuthor;

	@Inject
	IdentityManagerImpl(DatabaseComponent db) {
		this.db = db;
	}

	@Override
	public void registerLocalAuthor(LocalAuthor localAuthor) throws DbException {
		Transaction txn = db.startTransaction(false);
		try {
			db.addLocalAuthor(txn, localAuthor);
			txn.setComplete();
			cachedAuthor = localAuthor;
			LOG.info("Local author registered");
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public LocalAuthor getLocalAuthor() throws DbException {
		if (cachedAuthor == null) {
			Transaction txn = db.startTransaction(true);
			try {
				cachedAuthor = loadLocalAuthor(txn);
				LOG.info("Local author loaded");
				txn.setComplete();
			} finally {
				db.endTransaction(txn);
			}
		}
		return cachedAuthor;
	}


	@Override
	public LocalAuthor getLocalAuthor(Transaction txn) throws DbException {
		if (cachedAuthor == null) {
			cachedAuthor = loadLocalAuthor(txn);
			LOG.info("Local author loaded");
		}
		return cachedAuthor;
	}

	private LocalAuthor loadLocalAuthor(Transaction txn) throws  DbException{
		return db.getLocalAuthors(txn).iterator().next();
	}

	@Override
	public Status getAuthorStatus(AuthorId authorId) throws DbException {
		Transaction txn = db.startTransaction(true);
		try {
			return getAuthorStatus(txn, authorId);
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public Status getAuthorStatus(Transaction txn, AuthorId authorId)
			throws DbException {
		if (getLocalAuthor(txn).getId().equals(authorId)) return OURSELVES;
		Collection<Contact> contacts = db.getContactsByAuthorId(txn, authorId);
		if (contacts.isEmpty()) return UNKNOWN;
		for (Contact c : contacts) {
			if (c.isVerified()) return VERIFIED;
		}
		return UNVERIFIED;
	}

}
