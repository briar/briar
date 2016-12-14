package org.briarproject.bramble.identity;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.TestUtils;
import org.jmock.Expectations;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static org.briarproject.bramble.api.identity.Author.Status.OURSELVES;
import static org.briarproject.bramble.api.identity.Author.Status.UNKNOWN;
import static org.briarproject.bramble.api.identity.Author.Status.UNVERIFIED;
import static org.briarproject.bramble.api.identity.Author.Status.VERIFIED;
import static org.junit.Assert.assertEquals;

public class IdentityManagerImplTest extends BrambleMockTestCase {

	private final IdentityManager identityManager;
	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final Transaction txn = new Transaction(null, false);
	private final LocalAuthor localAuthor =
			new LocalAuthor(new AuthorId(TestUtils.getRandomId()),
					TestUtils.getRandomString(8), TestUtils.getRandomBytes(42),
					TestUtils.getRandomBytes(42), 0);
	private final Collection<LocalAuthor> localAuthors =
			Collections.singletonList(localAuthor);

	public IdentityManagerImplTest() {
		identityManager = new IdentityManagerImpl(db);
	}

	@Test
	public void testRegisterLocalAuthor() throws DbException {
		expectRegisterLocalAuthor();
		identityManager.registerLocalAuthor(localAuthor);
	}

	private void expectRegisterLocalAuthor() throws DbException {
		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(db).addLocalAuthor(txn, localAuthor);
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});
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
		expectRegisterLocalAuthor();
		identityManager.registerLocalAuthor(localAuthor);
		assertEquals(localAuthor, identityManager.getLocalAuthor());
	}

	@Test
	public void testGetAuthorStatus() throws DbException {
		final AuthorId authorId = new AuthorId(TestUtils.getRandomId());
		final Collection<Contact> contacts = new ArrayList<Contact>();

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
		Author author = new Author(authorId, TestUtils.getRandomString(8),
				TestUtils.getRandomBytes(42));
		Contact contact =
				new Contact(new ContactId(1), author, localAuthor.getId(),
						false, true);
		contacts.add(contact);

		checkAuthorStatusContext(authorId, contacts);
		assertEquals(UNVERIFIED, identityManager.getAuthorStatus(authorId));

		// add one verified contact
		Contact contact2 =
				new Contact(new ContactId(1), author, localAuthor.getId(),
						true, true);
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

	private void checkAuthorStatusContext(final AuthorId authorId,
			final Collection<Contact> contacts) throws DbException {
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
