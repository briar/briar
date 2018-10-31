package org.briarproject.bramble.contact;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.NoSuchContactException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.AuthorInfo;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.transport.KeyManager;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.DbExpectations;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Random;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.bramble.api.identity.AuthorInfo.Status.OURSELVES;
import static org.briarproject.bramble.api.identity.AuthorInfo.Status.UNKNOWN;
import static org.briarproject.bramble.api.identity.AuthorInfo.Status.UNVERIFIED;
import static org.briarproject.bramble.api.identity.AuthorInfo.Status.VERIFIED;
import static org.briarproject.bramble.test.TestUtils.getAuthor;
import static org.briarproject.bramble.test.TestUtils.getLocalAuthor;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ContactManagerImplTest extends BrambleMockTestCase {

	private final Mockery context = new Mockery();
	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final KeyManager keyManager = context.mock(KeyManager.class);
	private final IdentityManager identityManager =
			context.mock(IdentityManager.class);
	private final ContactManager contactManager;
	private final ContactId contactId = new ContactId(42);
	private final Author remote = getAuthor();
	private final AuthorId local = new AuthorId(getRandomId());
	private final LocalAuthor localAuthor = getLocalAuthor();
	private final String alias = getRandomString(MAX_AUTHOR_NAME_LENGTH);
	private final boolean verified = false, active = true;
	private final Contact contact =
			new Contact(contactId, remote, local, alias, verified, active);

	public ContactManagerImplTest() {
		contactManager = new ContactManagerImpl(db, keyManager, identityManager);
	}

	@Test
	public void testAddContact() throws Exception {
		SecretKey master = getSecretKey();
		long timestamp = System.currentTimeMillis();
		boolean alice = new Random().nextBoolean();
		Transaction txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(db).addContact(txn, remote, local, verified, active);
			will(returnValue(contactId));
			oneOf(keyManager).addContact(txn, contactId, master, timestamp,
					alice, active);
			oneOf(db).getContact(txn, contactId);
			will(returnValue(contact));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		assertEquals(contactId, contactManager.addContact(remote, local,
				master, timestamp, alice, verified, active));
	}

	@Test
	public void testGetContact() throws Exception {
		Transaction txn = new Transaction(null, true);
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
		Transaction txn = new Transaction(null, true);
		Collection<Contact> contacts = Collections.singleton(contact);
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
		Transaction txn = new Transaction(null, true);
		context.checking(new Expectations() {{
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getContactsByAuthorId(txn, remote.getId());
			will(returnValue(emptyList()));
			oneOf(db).endTransaction(txn);
		}});

		contactManager.getContact(remote.getId(), local);
	}

	@Test(expected = NoSuchContactException.class)
	public void testGetContactByUnknownLocalAuthor() throws Exception {
		Transaction txn = new Transaction(null, true);
		Collection<Contact> contacts = Collections.singleton(contact);
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
		Collection<Contact> contacts = new ArrayList<>(activeContacts);
		contacts.add(new Contact(new ContactId(3), remote, local, alias, true,
				false));
		Transaction txn = new Transaction(null, true);
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
		Transaction txn = new Transaction(null, false);
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
		Transaction txn = new Transaction(null, false);
		context.checking(new Expectations() {{
			oneOf(db).setContactActive(txn, contactId, active);
		}});

		contactManager.setContactActive(txn, contactId, active);
	}

	@Test
	public void testSetContactAlias() throws Exception {
		Transaction txn = new Transaction(null, false);
		context.checking(new DbExpectations() {{
			oneOf(db).transaction(with(equal(false)), withDbRunnable(txn));
			oneOf(db).setContactAlias(txn, contactId, alias);
		}});

		contactManager.setContactAlias(contactId, alias);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetContactAliasTooLong() throws Exception {
		Transaction txn = new Transaction(null, false);
		contactManager.setContactAlias(txn, contactId,
				getRandomString(MAX_AUTHOR_NAME_LENGTH + 1));
	}

	@Test
	public void testContactExists() throws Exception {
		Transaction txn = new Transaction(null, true);
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

	@Test
	public void testGetAuthorStatus() throws Exception {
		Transaction txn = new Transaction(null, true);
		Collection<Contact> contacts = singletonList(
				new Contact(new ContactId(1), remote, localAuthor.getId(),
						alias, false, true));

		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(equal(true)),
					withDbCallable(txn));
			oneOf(identityManager).getLocalAuthor(txn);
			will(returnValue(localAuthor));
			oneOf(db).getContactsByAuthorId(txn, remote.getId());
			will(returnValue(contacts));
		}});
		AuthorInfo authorInfo =
				contactManager.getAuthorInfo(txn, remote.getId());
		assertEquals(UNVERIFIED, authorInfo.getStatus());
		assertEquals(alias, contact.getAlias());
	}

	@Test
	public void testGetAuthorStatusTransaction() throws DbException {
		Transaction txn = new Transaction(null, true);

		// check unknown author
		context.checking(new Expectations() {{
			oneOf(identityManager).getLocalAuthor(txn);
			will(returnValue(localAuthor));
			oneOf(db).getContactsByAuthorId(txn, remote.getId());
			will(returnValue(emptyList()));
		}});
		AuthorInfo authorInfo =
				contactManager.getAuthorInfo(txn, remote.getId());
		assertEquals(UNKNOWN, authorInfo.getStatus());
		assertNull(authorInfo.getAlias());

		// check unverified contact
		Collection<Contact> contacts = singletonList(
				new Contact(new ContactId(1), remote, localAuthor.getId(),
						alias, false, true));
		checkAuthorStatusContext(txn, remote.getId(), contacts);
		authorInfo = contactManager.getAuthorInfo(txn, remote.getId());
		assertEquals(UNVERIFIED, authorInfo.getStatus());
		assertEquals(alias, contact.getAlias());

		// check verified contact
		contacts = singletonList(new Contact(new ContactId(1), remote,
				localAuthor.getId(), alias, true, true));
		checkAuthorStatusContext(txn, remote.getId(), contacts);
		authorInfo = contactManager.getAuthorInfo(txn, remote.getId());
		assertEquals(VERIFIED, authorInfo.getStatus());
		assertEquals(alias, contact.getAlias());

		// check ourselves
		context.checking(new Expectations() {{
			oneOf(identityManager).getLocalAuthor(txn);
			will(returnValue(localAuthor));
			never(db).getContactsByAuthorId(txn, remote.getId());
		}});
		authorInfo = contactManager.getAuthorInfo(txn, localAuthor.getId());
		assertEquals(OURSELVES, authorInfo.getStatus());
		assertNull(authorInfo.getAlias());
	}

	private void checkAuthorStatusContext(Transaction txn, AuthorId authorId,
			Collection<Contact> contacts) throws DbException {
		context.checking(new Expectations() {{
			oneOf(identityManager).getLocalAuthor(txn);
			will(returnValue(localAuthor));
			oneOf(db).getContactsByAuthorId(txn, authorId);
			will(returnValue(contacts));
		}});
	}

}
