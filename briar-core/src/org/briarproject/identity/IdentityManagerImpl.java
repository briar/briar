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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.inject.Inject;

import static org.briarproject.api.identity.Author.Status.OURSELVES;
import static org.briarproject.api.identity.Author.Status.UNKNOWN;
import static org.briarproject.api.identity.Author.Status.UNVERIFIED;
import static org.briarproject.api.identity.Author.Status.VERIFIED;

class IdentityManagerImpl implements IdentityManager {
	private final DatabaseComponent db;
	private final List<AddIdentityHook> addHooks;
	private final List<RemoveIdentityHook> removeHooks;

	@Inject
	IdentityManagerImpl(DatabaseComponent db) {
		this.db = db;
		addHooks = new CopyOnWriteArrayList<AddIdentityHook>();
		removeHooks = new CopyOnWriteArrayList<RemoveIdentityHook>();
	}

	@Override
	public void registerAddIdentityHook(AddIdentityHook hook) {
		addHooks.add(hook);
	}

	@Override
	public void registerRemoveIdentityHook(RemoveIdentityHook hook) {
		removeHooks.add(hook);
	}

	@Override
	public void addLocalAuthor(LocalAuthor localAuthor) throws DbException {
		Transaction txn = db.startTransaction(false);
		try {
			db.addLocalAuthor(txn, localAuthor);
			for (AddIdentityHook hook : addHooks)
				hook.addingIdentity(txn, localAuthor);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public LocalAuthor getLocalAuthor(AuthorId a) throws DbException {
		LocalAuthor author;
		Transaction txn = db.startTransaction(true);
		try {
			author = getLocalAuthor(txn, a);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
		return author;
	}

	@Override
	public LocalAuthor getLocalAuthor(Transaction txn, AuthorId a)
			throws DbException {
		return db.getLocalAuthor(txn, a);
	}

	@Override
	public LocalAuthor getLocalAuthor() throws DbException {
		return getLocalAuthors().iterator().next();
	}

	@Override
	public LocalAuthor getLocalAuthor(Transaction txn) throws DbException {
		return getLocalAuthors(txn).iterator().next();
	}

	@Override
	public Collection<LocalAuthor> getLocalAuthors() throws DbException {
		Collection<LocalAuthor> authors;
		Transaction txn = db.startTransaction(true);
		try {
			authors = getLocalAuthors(txn);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
		return authors;
	}

	private Collection<LocalAuthor> getLocalAuthors(Transaction txn)
			throws DbException {

		return db.getLocalAuthors(txn);
	}

	@Override
	public void removeLocalAuthor(AuthorId a) throws DbException {
		Transaction txn = db.startTransaction(false);
		try {
			LocalAuthor localAuthor = db.getLocalAuthor(txn, a);
			for (RemoveIdentityHook hook : removeHooks)
				hook.removingIdentity(txn, localAuthor);
			db.removeLocalAuthor(txn, a);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public Status getAuthorStatus(AuthorId authorId) throws DbException {
		Transaction txn = db.startTransaction(false);
		try {
			return getAuthorStatus(txn, authorId);
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public Status getAuthorStatus(Transaction txn, AuthorId authorId)
			throws DbException {

		// Compare to the IDs of the user's identities
		for (LocalAuthor a : db.getLocalAuthors(txn)) {
			if (a.getId().equals(authorId)) return OURSELVES;
		}

		Collection<Contact> contacts = db.getContactsByAuthorId(txn, authorId);
		if (contacts.isEmpty()) return UNKNOWN;
		for (Contact c : contacts) {
			if (c.isVerified()) return VERIFIED;
		}
		return UNVERIFIED;
	}

}
