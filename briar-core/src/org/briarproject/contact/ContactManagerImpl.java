package org.briarproject.contact;

import com.google.inject.Inject;

import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;

import java.util.Collection;

class ContactManagerImpl implements ContactManager {

	private final DatabaseComponent db;

	@Inject
	ContactManagerImpl(DatabaseComponent db) {
		this.db = db;
	}

	@Override
	public ContactId addContact(Author remote, AuthorId local)
			throws DbException {
		return db.addContact(remote, local);
	}

	@Override
	public Contact getContact(ContactId c) throws DbException {
		return db.getContact(c);
	}

	@Override
	public Collection<Contact> getContacts() throws DbException {
		return db.getContacts();
	}

	@Override
	public void removeContact(ContactId c) throws DbException {
		db.removeContact(c);
	}
}
