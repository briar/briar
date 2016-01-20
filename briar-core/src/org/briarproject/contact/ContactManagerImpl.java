package org.briarproject.contact;

import com.google.inject.Inject;

import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.NoSuchContactException;
import org.briarproject.api.event.ContactAddedEvent;
import org.briarproject.api.event.ContactRemovedEvent;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.IdentityManager.IdentityRemovedHook;
import org.briarproject.api.lifecycle.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import static org.briarproject.api.contact.Contact.Status.ACTIVE;
import static org.briarproject.api.contact.Contact.Status.ADDING;
import static org.briarproject.api.contact.Contact.Status.REMOVING;

class ContactManagerImpl implements ContactManager, Service,
		IdentityRemovedHook {

	private static final Logger LOG =
			Logger.getLogger(ContactManagerImpl.class.getName());

	private final DatabaseComponent db;
	private final EventBus eventBus;
	private final List<ContactAddedHook> addHooks;
	private final List<ContactRemovedHook> removeHooks;

	@Inject
	ContactManagerImpl(DatabaseComponent db, EventBus eventBus) {
		this.db = db;
		this.eventBus = eventBus;
		addHooks = new CopyOnWriteArrayList<ContactAddedHook>();
		removeHooks = new CopyOnWriteArrayList<ContactRemovedHook>();
	}

	@Override
	public boolean start() {
		// Finish adding/removing any partly added/removed contacts
		try {
			for (Contact c : db.getContacts()) {
				if (c.getStatus().equals(ADDING)) {
					for (ContactAddedHook hook : addHooks)
						hook.contactAdded(c.getId());
					db.setContactStatus(c.getId(), ACTIVE);
					eventBus.broadcast(new ContactAddedEvent(c.getId()));
				} else if (c.getStatus().equals(REMOVING)) {
					for (ContactRemovedHook hook : removeHooks)
						hook.contactRemoved(c.getId());
					db.removeContact(c.getId());
					eventBus.broadcast(new ContactRemovedEvent(c.getId()));
				}
			}
			return true;
		} catch (DbException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return false;
		}
	}

	@Override
	public boolean stop() {
		return true;
	}

	@Override
	public void registerContactAddedHook(ContactAddedHook hook) {
		addHooks.add(hook);
	}

	@Override
	public void registerContactRemovedHook(ContactRemovedHook hook) {
		removeHooks.add(hook);
	}

	@Override
	public ContactId addContact(Author remote, AuthorId local)
			throws DbException {
		ContactId c = db.addContact(remote, local);
		for (ContactAddedHook hook : addHooks) hook.contactAdded(c);
		db.setContactStatus(c, ACTIVE);
		eventBus.broadcast(new ContactAddedEvent(c));
		return c;
	}

	@Override
	public Contact getContact(ContactId c) throws DbException {
		Contact contact = db.getContact(c);
		if (contact.getStatus().equals(ACTIVE)) return contact;
		throw new NoSuchContactException();
	}

	@Override
	public Collection<Contact> getContacts() throws DbException {
		Collection<Contact> contacts = db.getContacts();
		// Filter out any contacts that are being added or removed
		List<Contact> active = new ArrayList<Contact>(contacts.size());
		for (Contact c : contacts)
			if (c.getStatus().equals(ACTIVE)) active.add(c);
		return Collections.unmodifiableList(active);
	}

	@Override
	public void removeContact(ContactId c) throws DbException {
		db.setContactStatus(c, REMOVING);
		for (ContactRemovedHook hook : removeHooks) hook.contactRemoved(c);
		db.removeContact(c);
		eventBus.broadcast(new ContactRemovedEvent(c));
	}

	@Override
	public void identityRemoved(AuthorId a) {
		// Remove any contacts of the local pseudonym that's being removed
		try {
			for (ContactId c : db.getContacts(a)) removeContact(c);
		} catch (DbException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}
}
