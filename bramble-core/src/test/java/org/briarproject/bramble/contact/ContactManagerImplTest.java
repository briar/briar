package org.briarproject.bramble.contact;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.NoSuchContactException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.transport.KeyManager;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ContactManagerImplTest extends BrambleMockTestCase {

	private final Mockery context = new Mockery();
	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final KeyManager keyManager = context.mock(KeyManager.class);
	private final ContactManager contactManager;
	private final ContactId contactId = new ContactId(42);
	private final Author remote =
			new Author(new AuthorId(getRandomId()), "remote",
					getRandomBytes(42));
	private final AuthorId local = new AuthorId(getRandomId());
	private final boolean verified = false, active = true;
	private final Contact contact =
			new Contact(contactId, remote, local, verified, active);

	public ContactManagerImplTest() {
		contactManager = new ContactManagerImpl(db, keyManager);
	}

	@Test
	public void testAddContact() throws Exception {
		final SecretKey master = getSecretKey();
		final long timestamp = 42;
		final boolean alice = true;
		final Transaction txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(db).addContact(txn, remote, local, verified, active);
			will(returnValue(contactId));
			oneOf(keyManager)
					.addContact(txn, contactId, master, timestamp, alice);
			oneOf(db).getContact(txn, contactId);
			will(returnValue(contact));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		assertEquals(contactId, contactManager
				.addContact(remote, local, master, timestamp, alice, verified,
						active));
	}

	@Test
	public void testGetContact() throws Exception {
		final Transaction txn = new Transaction(null, true);
		context.checking(new Expectations() {{
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getContact(txn, contactId);
			will(returnValue(contact));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		assertEquals(contact, contactManager.getContact(contactId));
	}

	@Test
	public void testGetContactByAuthor() throws Exception {
		final Transaction txn = new Transaction(null, true);
		final Collection<Contact> contacts = Collections.singleton(contact);
		context.checking(new Expectations() {{
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getContactsByAuthorId(txn, remote.getId());
			will(returnValue(contacts));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		assertEquals(contact, contactManager.getContact(remote.getId(), local));
	}

	@Test(expected = NoSuchContactException.class)
	public void testGetContactByUnknownAuthor() throws Exception {
		final Transaction txn = new Transaction(null, true);
		context.checking(new Expectations() {{
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getContactsByAuthorId(txn, remote.getId());
			will(returnValue(Collections.emptyList()));
			oneOf(db).endTransaction(txn);
		}});

		contactManager.getContact(remote.getId(), local);
	}

	@Test(expected = NoSuchContactException.class)
	public void testGetContactByUnknownLocalAuthor() throws Exception {
		final Transaction txn = new Transaction(null, true);
		final Collection<Contact> contacts = Collections.singleton(contact);
		context.checking(new Expectations() {{
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getContactsByAuthorId(txn, remote.getId());
			will(returnValue(contacts));
			oneOf(db).endTransaction(txn);
		}});

		contactManager.getContact(remote.getId(), new AuthorId(getRandomId()));
	}

	@Test
	public void testActiveContacts() throws Exception {
		Collection<Contact> activeContacts = Collections.singletonList(contact);
		final Collection<Contact> contacts =
				new ArrayList<Contact>(activeContacts);
		contacts.add(new Contact(new ContactId(3), remote, local, true, false));
		final Transaction txn = new Transaction(null, true);
		context.checking(new Expectations() {{
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getContacts(txn);
			will(returnValue(contacts));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		assertEquals(activeContacts, contactManager.getActiveContacts());
	}

	@Test
	public void testRemoveContact() throws Exception {
		final Transaction txn = new Transaction(null, false);
		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(db).getContact(txn, contactId);
			will(returnValue(contact));
			oneOf(db).removeContact(txn, contactId);
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		contactManager.removeContact(contactId);
	}

	@Test
	public void testSetContactActive() throws Exception {
		final Transaction txn = new Transaction(null, false);
		context.checking(new Expectations() {{
			oneOf(db).setContactActive(txn, contactId, active);
		}});

		contactManager.setContactActive(txn, contactId, active);
	}

	@Test
	public void testContactExists() throws Exception {
		final Transaction txn = new Transaction(null, true);
		context.checking(new Expectations() {{
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).containsContact(txn, remote.getId(), local);
			will(returnValue(true));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		assertTrue(contactManager.contactExists(remote.getId(), local));
	}

}
