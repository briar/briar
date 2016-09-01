package org.briarproject.identity;

import org.briarproject.BriarTestCase;
import org.briarproject.TestUtils;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;

import static org.briarproject.api.identity.Author.Status.OURSELVES;
import static org.briarproject.api.identity.Author.Status.UNKNOWN;
import static org.briarproject.api.identity.Author.Status.UNVERIFIED;
import static org.briarproject.api.identity.Author.Status.VERIFIED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class IdentityManagerImplTest extends BriarTestCase {

	private final Mockery context;
	private final IdentityManager identityManager;
	private final DatabaseComponent db;
	private final Transaction txn;

	public IdentityManagerImplTest() {
		context = new Mockery();
		db = context.mock(DatabaseComponent.class);
		txn = new Transaction(null, false);
		identityManager = new IdentityManagerImpl(db);
	}

	@Test
	public void testUnitTestsExist() {
		fail(); // FIXME: Write more tests
	}

	@Test
	public void testGetAuthorStatus() throws DbException {
		AuthorId authorId = new AuthorId(TestUtils.getRandomId());
		final Collection<LocalAuthor> localAuthors = new ArrayList<>();
		LocalAuthor localAuthor =
				new LocalAuthor(new AuthorId(TestUtils.getRandomId()),
						TestUtils.getRandomString(8),
						TestUtils.getRandomBytes(42),
						TestUtils.getRandomBytes(42), 0);
		localAuthors.add(localAuthor);
		Collection<Contact> contacts = new ArrayList<>();

		checkAuthorStatusContext(localAuthors, authorId, contacts);
		assertEquals(UNKNOWN, identityManager.getAuthorStatus(authorId));

		// add one unverified contact
		Author author = new Author(authorId, TestUtils.getRandomString(8),
				TestUtils.getRandomBytes(42));
		Contact contact =
				new Contact(new ContactId(1), author, localAuthor.getId(),
						false, true);
		contacts.add(contact);

		checkAuthorStatusContext(localAuthors, authorId, contacts);
		assertEquals(UNVERIFIED, identityManager.getAuthorStatus(authorId));

		// add one verified contact
		Contact contact2 =
				new Contact(new ContactId(1), author, localAuthor.getId(),
						true, true);
		contacts.add(contact2);

		checkAuthorStatusContext(localAuthors, authorId, contacts);
		assertEquals(VERIFIED, identityManager.getAuthorStatus(authorId));

		// add ourselves to the local authors
		LocalAuthor localAuthor2 =
				new LocalAuthor(authorId,
						TestUtils.getRandomString(8),
						TestUtils.getRandomBytes(42),
						TestUtils.getRandomBytes(42), 0);
		localAuthors.add(localAuthor2);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(db).getLocalAuthors(txn);
			will(returnValue(localAuthors));
			oneOf(db).endTransaction(txn);
		}});
		assertEquals(OURSELVES, identityManager.getAuthorStatus(authorId));

		context.assertIsSatisfied();
	}

	private void checkAuthorStatusContext(
			final Collection<LocalAuthor> localAuthors, final AuthorId authorId,
			final Collection<Contact> contacts) throws DbException {
		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(db).getLocalAuthors(txn);
			will(returnValue(localAuthors));
			oneOf(db).getContactsByAuthorId(txn, authorId);
			will(returnValue(contacts));
			oneOf(db).endTransaction(txn);
		}});
	}

}
