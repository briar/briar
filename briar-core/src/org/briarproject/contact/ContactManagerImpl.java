package org.briarproject.contact;

import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.IdentityManager.RemoveIdentityHook;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.transport.KeyManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.inject.Inject;

class ContactManagerImpl implements ContactManager, RemoveIdentityHook {

	private final DatabaseComponent db;
	private final KeyManager keyManager;
	private final List<AddContactHook> addHooks;
	private final List<RemoveContactHook> removeHooks;

	@Inject
	ContactManagerImpl(DatabaseComponent db, KeyManager keyManager) {
		this.db = db;
		this.keyManager = keyManager;
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
	public ContactId addContact(Author remote, AuthorId local, SecretKey master,
			long timestamp, boolean alice, boolean active)
			throws DbException {
		ContactId c;
		Transaction txn = db.startTransaction();
		try {
			c = db.addContact(txn, remote, local, active);
			keyManager.addContact(txn, c, master, timestamp, alice);
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
	public Collection<Contact> getActiveContacts() throws DbException {
		Collection<Contact> contacts;
		Transaction txn = db.startTransaction();
		try {
			contacts = db.getContacts(txn);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
		List<Contact> active = new ArrayList<Contact>(contacts.size());
		for (Contact c : contacts) if (c.isActive()) active.add(c);
		return Collections.unmodifiableList(active);
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

	@Override
	public void setContactActive(ContactId c, boolean active)
			throws DbException {
		Transaction txn = db.startTransaction();
		try {
			db.setContactActive(txn, c, active);
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
