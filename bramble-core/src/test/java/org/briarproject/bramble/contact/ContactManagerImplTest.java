package org.briarproject.bramble.contact;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorInfo;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.transport.KeyManager;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.DbExpectations;
import org.jmock.Mockery;
import org.junit.Test;

import java.util.Collection;
import java.util.Random;

import static java.util.Collections.singletonList;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.bramble.api.identity.AuthorInfo.Status.OURSELVES;
import static org.briarproject.bramble.api.identity.AuthorInfo.Status.UNKNOWN;
import static org.briarproject.bramble.api.identity.AuthorInfo.Status.UNVERIFIED;
import static org.briarproject.bramble.api.identity.AuthorInfo.Status.VERIFIED;
import static org.briarproject.bramble.test.TestUtils.getAuthor;
import static org.briarproject.bramble.test.TestUtils.getContact;
import static org.briarproject.bramble.test.TestUtils.getLocalAuthor;
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
	private final Author author = getAuthor();
	private final LocalAuthor localAuthor = getLocalAuthor();
	private final boolean verified = false, active = true;
	private final Contact contact = getContact(author, verified);
	private final ContactId contactId = contact.getId();

	public ContactManagerImplTest() {
		contactManager =
				new ContactManagerImpl(db, keyManager, identityManager);
	}

	@Test
	public void testAddContact() throws Exception {
		SecretKey rootKey = getSecretKey();
		long timestamp = System.currentTimeMillis();
		boolean alice = new Random().nextBoolean();
		Transaction txn = new Transaction(null, false);

		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(false), withDbCallable(txn));
			oneOf(db).addContact(txn, author, verified);
			will(returnValue(contactId));
			oneOf(keyManager).addContact(txn, contactId, rootKey, timestamp,
					alice, active);
			oneOf(db).getContact(txn, contactId);
			will(returnValue(contact));
		}});

		assertEquals(contactId, contactManager.addContact(author, rootKey,
				timestamp, alice, verified, active));
	}

	@Test
	public void testGetContactByContactId() throws Exception {
		Transaction txn = new Transaction(null, true);
		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).getContact(txn, contactId);
			will(returnValue(contact));
		}});

		assertEquals(contact, contactManager.getContact(contactId));
	}

	@Test
	public void testGetContactByAuthorId() throws Exception {
		Transaction txn = new Transaction(null, true);
		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).getContact(txn, author.getId());
			will(returnValue(contact));
		}});

		assertEquals(contact, contactManager.getContact(author.getId()));
	}

	@Test
	public void testGetContacts() throws Exception {
		Collection<Contact> contacts = singletonList(contact);
		Transaction txn = new Transaction(null, true);
		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).getContacts(txn);
			will(returnValue(contacts));
		}});

		assertEquals(contacts, contactManager.getContacts());
	}

	@Test
	public void testRemoveContact() throws Exception {
		Transaction txn = new Transaction(null, false);
		context.checking(new DbExpectations() {{
			oneOf(db).transaction(with(false), withDbRunnable(txn));
			oneOf(db).getContact(txn, contactId);
			will(returnValue(contact));
			oneOf(db).removeContact(txn, contactId);
		}});

		contactManager.removeContact(contactId);
	}

	@Test
	public void testSetContactAlias() throws Exception {
		Transaction txn = new Transaction(null, false);
		String alias = getRandomString(MAX_AUTHOR_NAME_LENGTH);

		context.checking(new DbExpectations() {{
			oneOf(db).transaction(with(false), withDbRunnable(txn));
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
		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).containsContact(txn, author.getId());
			will(returnValue(true));
		}});

		assertTrue(contactManager.contactExists(author.getId()));
	}

	@Test
	public void testGetAuthorInfoOurselves() throws Exception {
		Transaction txn = new Transaction(null, true);

		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(identityManager).getLocalAuthor(txn);
			will(returnValue(localAuthor));
		}});
		AuthorInfo authorInfo =
				contactManager.getAuthorInfo(txn, localAuthor.getId());
		assertEquals(OURSELVES, authorInfo.getStatus());
		assertNull(authorInfo.getAlias());
	}

	@Test
	public void testGetAuthorInfoVerified() throws Exception {
		Transaction txn = new Transaction(null, true);
		Contact verified = getContact(author, true);

		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(identityManager).getLocalAuthor(txn);
			will(returnValue(localAuthor));
			oneOf(db).containsContact(txn, author.getId());
			will(returnValue(true));
			oneOf(db).getContact(txn, author.getId());
			will(returnValue(verified));
		}});
		AuthorInfo authorInfo =
				contactManager.getAuthorInfo(txn, author.getId());
		assertEquals(VERIFIED, authorInfo.getStatus());
		assertEquals(verified.getAlias(), authorInfo.getAlias());
	}

	@Test
	public void testGetAuthorInfoUnverified() throws Exception {
		Transaction txn = new Transaction(null, true);
		Contact unverified = getContact(author, false);

		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(identityManager).getLocalAuthor(txn);
			will(returnValue(localAuthor));
			oneOf(db).containsContact(txn, author.getId());
			will(returnValue(true));
			oneOf(db).getContact(txn, author.getId());
			will(returnValue(unverified));
		}});
		AuthorInfo authorInfo =
				contactManager.getAuthorInfo(txn, author.getId());
		assertEquals(UNVERIFIED, authorInfo.getStatus());
		assertEquals(unverified.getAlias(), authorInfo.getAlias());
	}

	@Test
	public void testGetAuthorInfoUnknown() throws Exception {
		Transaction txn = new Transaction(null, true);

		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(identityManager).getLocalAuthor(txn);
			will(returnValue(localAuthor));
			oneOf(db).containsContact(txn, author.getId());
			will(returnValue(false));
		}});
		AuthorInfo authorInfo =
				contactManager.getAuthorInfo(txn, author.getId());
		assertEquals(UNKNOWN, authorInfo.getStatus());
		assertNull(authorInfo.getAlias());
	}
}
