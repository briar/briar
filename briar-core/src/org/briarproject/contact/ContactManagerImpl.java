package org.briarproject.contact;

import com.google.inject.Inject;

import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.IdentityManager.RemoveIdentityHook;
import org.briarproject.api.identity.LocalAuthor;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

class ContactManagerImpl implements ContactManager, RemoveIdentityHook {

	private final DatabaseComponent db;
	private final List<AddContactHook> addHooks;
	private final List<RemoveContactHook> removeHooks;

	@Inject
	ContactManagerImpl(DatabaseComponent db) {
		this.db = db;
		addHooks = new CopyOnWriteArrayList<AddContactHook>();
		removeHooks = new CopyOnWriteArrayList<RemoveContactHook>();
	}

	@Override
	public void registerAddContactHook(AddContactHook hook) {
		addHooks.add(hook);
	}

	@Override
	public void registerRemoveContactHook(RemoveContactHook hook) {
		removeHooks.add(hook);
	}

	@Override
	public ContactId addContact(Author remote, AuthorId local)
			throws DbException {
		ContactId c;
		Transaction txn = db.startTransaction();
		try {
			c = db.addContact(txn, remote, local);
			Contact contact = db.getContact(txn, c);
			for (AddContactHook hook : addHooks)
				hook.addingContact(txn, contact);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
		return c;
	}

	@Override
	public Contact getContact(ContactId c) throws DbException {
		Contact contact;
		Transaction txn = db.startTransaction();
		try {
			contact = db.getContact(txn, c);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
		return contact;
	}

	@Override
	public Collection<Contact> getContacts() throws DbException {
		Collection<Contact> contacts;
		Transaction txn = db.startTransaction();
		try {
			contacts = db.getContacts(txn);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
		return contacts;
	}

	@Override
	public void removeContact(ContactId c) throws DbException {
		Transaction txn = db.startTransaction();
		try {
			removeContact(txn, c);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
	}

	private void removeContact(Transaction txn, ContactId c)
			throws DbException {
		Contact contact = db.getContact(txn, c);
		for (RemoveContactHook hook : removeHooks)
			hook.removingContact(txn, contact);
		db.removeContact(txn, c);
	}

	@Override
	public void removingIdentity(Transaction txn, LocalAuthor a)
			throws DbException {
		// Remove any contacts of the local pseudonym that's being removed
		for (ContactId c : db.getContacts(txn, a.getId()))
			removeContact(txn, c);
	}
}
