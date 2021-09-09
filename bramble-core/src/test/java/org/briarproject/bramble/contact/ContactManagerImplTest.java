package org.briarproject.bramble.contact;

import org.briarproject.bramble.api.Pair;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.PendingContact;
import org.briarproject.bramble.api.contact.PendingContactState;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.NoSuchContactException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.transport.KeyManager;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.DbExpectations;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.Random;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.briarproject.bramble.api.contact.HandshakeLinkConstants.BASE32_LINK_BYTES;
import static org.briarproject.bramble.api.contact.PendingContactState.WAITING_FOR_CONNECTION;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.bramble.test.TestUtils.getAgreementPrivateKey;
import static org.briarproject.bramble.test.TestUtils.getAgreementPublicKey;
import static org.briarproject.bramble.test.TestUtils.getAuthor;
import static org.briarproject.bramble.test.TestUtils.getContact;
import static org.briarproject.bramble.test.TestUtils.getLocalAuthor;
import static org.briarproject.bramble.test.TestUtils.getPendingContact;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.briarproject.bramble.util.StringUtils.getRandomBase32String;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ContactManagerImplTest extends BrambleMockTestCase {

	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final KeyManager keyManager = context.mock(KeyManager.class);
	private final IdentityManager identityManager =
			context.mock(IdentityManager.class);
	private final PendingContactFactory pendingContactFactory =
			context.mock(PendingContactFactory.class);

	private final Author remote = getAuthor();
	private final LocalAuthor localAuthor = getLocalAuthor();
	private final AuthorId local = localAuthor.getId();
	private final boolean verified = false, active = true;
	private final Contact contact = getContact(remote, local, verified);
	private final ContactId contactId = contact.getId();
	private final KeyPair handshakeKeyPair =
			new KeyPair(getAgreementPublicKey(), getAgreementPrivateKey());
	private final PendingContact pendingContact = getPendingContact();
	private final SecretKey rootKey = getSecretKey();
	private final long timestamp = System.currentTimeMillis();
	private final boolean alice = new Random().nextBoolean();

	private ContactManagerImpl contactManager;

	@Before
	public void setUp() {
		contactManager = new ContactManagerImpl(db, keyManager,
				identityManager, pendingContactFactory);
	}

	@Test
	public void testAddContact() throws Exception {
		Transaction txn = new Transaction(null, false);

		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(false), withDbCallable(txn));
			oneOf(db).addContact(txn, remote, local, null, verified);
			will(returnValue(contactId));
			oneOf(keyManager).addRotationKeys(txn, contactId, rootKey,
					timestamp, alice, active);
			oneOf(db).getContact(txn, contactId);
			will(returnValue(contact));
		}});

		assertEquals(contactId, contactManager.addContact(remote, local,
				rootKey, timestamp, alice, verified, active));
	}

	@Test
	public void testGetContact() throws Exception {
		Transaction txn = new Transaction(null, true);

		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).getContact(txn, contactId);
			will(returnValue(contact));
		}});

		assertEquals(contact, contactManager.getContact(contactId));
	}

	@Test
	public void testGetContactByAuthor() throws Exception {
		Transaction txn = new Transaction(null, true);
		Collection<Contact> contacts = singletonList(contact);

		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).getContactsByAuthorId(txn, remote.getId());
			will(returnValue(contacts));
		}});

		assertEquals(contact, contactManager.getContact(remote.getId(), local));
	}

	@Test(expected = NoSuchContactException.class)
	public void testGetContactByUnknownAuthor() throws Exception {
		Transaction txn = new Transaction(null, true);

		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).getContactsByAuthorId(txn, remote.getId());
			will(returnValue(emptyList()));
		}});

		contactManager.getContact(remote.getId(), local);
	}

	@Test(expected = NoSuchContactException.class)
	public void testGetContactByUnknownLocalAuthor() throws Exception {
		Transaction txn = new Transaction(null, true);
		Collection<Contact> contacts = singletonList(contact);

		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).getContactsByAuthorId(txn, remote.getId());
			will(returnValue(contacts));
		}});

		contactManager.getContact(remote.getId(), new AuthorId(getRandomId()));
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
			oneOf(db).containsContact(txn, remote.getId(), local);
			will(returnValue(true));
		}});

		assertTrue(contactManager.contactExists(remote.getId(), local));
	}

	@Test
	public void testGetHandshakeLink() throws Exception {
		Transaction txn = new Transaction(null, true);
		String link = "briar://" + getRandomBase32String(BASE32_LINK_BYTES);

		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(identityManager).getHandshakeKeys(txn);
			will(returnValue(handshakeKeyPair));
			oneOf(pendingContactFactory).createHandshakeLink(
					handshakeKeyPair.getPublic());
			will(returnValue(link));
		}});

		assertEquals(link, contactManager.getHandshakeLink());
	}

	@Test
	public void testDefaultPendingContactState() throws Exception {
		Transaction txn = new Transaction(null, true);

		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).getPendingContacts(txn);
			will(returnValue(singletonList(pendingContact)));
		}});

		// No events have happened for this pending contact, so the state
		// should be WAITING_FOR_CONNECTION
		Collection<Pair<PendingContact, PendingContactState>> pairs =
				contactManager.getPendingContacts();
		assertEquals(1, pairs.size());
		Pair<PendingContact, PendingContactState> pair =
				pairs.iterator().next();
		assertEquals(pendingContact, pair.getFirst());
		assertEquals(WAITING_FOR_CONNECTION, pair.getSecond());
	}

}
