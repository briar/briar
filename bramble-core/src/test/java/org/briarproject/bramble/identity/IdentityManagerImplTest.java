package org.briarproject.bramble.identity;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.PrivateKey;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.jmock.Expectations;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static org.briarproject.bramble.api.identity.Author.Status.OURSELVES;
import static org.briarproject.bramble.api.identity.Author.Status.UNKNOWN;
import static org.briarproject.bramble.api.identity.Author.Status.UNVERIFIED;
import static org.briarproject.bramble.api.identity.Author.Status.VERIFIED;
import static org.briarproject.bramble.test.TestUtils.getAuthor;
import static org.briarproject.bramble.test.TestUtils.getLocalAuthor;
import static org.junit.Assert.assertEquals;

public class IdentityManagerImplTest extends BrambleMockTestCase {

	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final CryptoComponent crypto = context.mock(CryptoComponent.class);
	private final AuthorFactory authorFactory =
			context.mock(AuthorFactory.class);
	private final PublicKey publicKey = context.mock(PublicKey.class);
	private final PrivateKey privateKey = context.mock(PrivateKey.class);

	private final Transaction txn = new Transaction(null, false);
	private final LocalAuthor localAuthor = getLocalAuthor();
	private final Collection<LocalAuthor> localAuthors =
			Collections.singletonList(localAuthor);
	private final String authorName = localAuthor.getName();
	private final KeyPair keyPair = new KeyPair(publicKey, privateKey);
	private final byte[] publicKeyBytes = localAuthor.getPublicKey();
	private final byte[] privateKeyBytes = localAuthor.getPrivateKey();
	private IdentityManager identityManager;

	@Before
	public void setUp() {
		identityManager = new IdentityManagerImpl(db, crypto, authorFactory);
	}

	@Test
	public void testCreateLocalAuthor() {
		context.checking(new Expectations() {{
			oneOf(crypto).generateSignatureKeyPair();
			will(returnValue(keyPair));
			oneOf(publicKey).getEncoded();
			will(returnValue(publicKeyBytes));
			oneOf(privateKey).getEncoded();
			will(returnValue(privateKeyBytes));
			oneOf(authorFactory).createLocalAuthor(authorName,
					publicKeyBytes, privateKeyBytes);
			will(returnValue(localAuthor));
		}});

		assertEquals(localAuthor,
				identityManager.createLocalAuthor(authorName));
	}

	@Test
	public void testRegisterAndStoreLocalAuthor() throws DbException {
		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(db).addLocalAuthor(txn, localAuthor);
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		identityManager.registerLocalAuthor(localAuthor);
		assertEquals(localAuthor, identityManager.getLocalAuthor());
		identityManager.storeLocalAuthor();
	}

	@Test
	public void testGetLocalAuthor() throws DbException {
		context.checking(new Expectations() {{
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getLocalAuthors(txn);
			will(returnValue(localAuthors));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});
		assertEquals(localAuthor, identityManager.getLocalAuthor());
	}

	@Test
	public void testGetCachedLocalAuthor() throws DbException {
		identityManager.registerLocalAuthor(localAuthor);
		assertEquals(localAuthor, identityManager.getLocalAuthor());
	}

	@Test
	public void testGetAuthorStatus() throws DbException {
		Author author = getAuthor();
		AuthorId authorId = author.getId();
		Collection<Contact> contacts = new ArrayList<>();

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getLocalAuthors(txn);
			will(returnValue(localAuthors));
			oneOf(db).getContactsByAuthorId(txn, authorId);
			will(returnValue(contacts));
			oneOf(db).endTransaction(txn);
		}});
		assertEquals(UNKNOWN, identityManager.getAuthorStatus(authorId));

		// add one unverified contact
		Contact contact = new Contact(new ContactId(1), author,
				localAuthor.getId(), false, true);
		contacts.add(contact);

		checkAuthorStatusContext(authorId, contacts);
		assertEquals(UNVERIFIED, identityManager.getAuthorStatus(authorId));

		// add one verified contact
		Contact contact2 = new Contact(new ContactId(1), author,
				localAuthor.getId(), true, true);
		contacts.add(contact2);

		checkAuthorStatusContext(authorId, contacts);
		assertEquals(VERIFIED, identityManager.getAuthorStatus(authorId));

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			never(db).getLocalAuthors(txn);
			never(db).getContactsByAuthorId(txn, authorId);
			oneOf(db).endTransaction(txn);
		}});
		assertEquals(OURSELVES,
				identityManager.getAuthorStatus(localAuthor.getId()));
	}

	private void checkAuthorStatusContext(AuthorId authorId,
			Collection<Contact> contacts) throws DbException {
		context.checking(new Expectations() {{
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			never(db).getLocalAuthors(txn);
			oneOf(db).getContactsByAuthorId(txn, authorId);
			will(returnValue(contacts));
			oneOf(db).endTransaction(txn);
		}});
	}

}
