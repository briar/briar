package org.briarproject.blogs;

import org.briarproject.BriarTestCase;
import org.briarproject.api.FormatException;
import org.briarproject.api.blogs.Blog;
import org.briarproject.api.blogs.BlogFactory;
import org.briarproject.api.blogs.BlogPost;
import org.briarproject.api.blogs.BlogPostFactory;
import org.briarproject.api.blogs.BlogPostHeader;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfEntry;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataParser;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.event.BlogPostAddedEvent;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;

import javax.inject.Inject;

import static org.briarproject.TestUtils.getRandomBytes;
import static org.briarproject.TestUtils.getRandomId;
import static org.briarproject.api.blogs.BlogConstants.KEY_AUTHOR;
import static org.briarproject.api.blogs.BlogConstants.KEY_AUTHOR_ID;
import static org.briarproject.api.blogs.BlogConstants.KEY_AUTHOR_NAME;
import static org.briarproject.api.blogs.BlogConstants.KEY_PUBLIC_KEY;
import static org.briarproject.api.blogs.BlogConstants.KEY_READ;
import static org.briarproject.api.blogs.BlogConstants.KEY_TIMESTAMP;
import static org.briarproject.api.blogs.BlogConstants.KEY_TIME_RECEIVED;
import static org.briarproject.api.blogs.BlogConstants.KEY_TYPE;
import static org.briarproject.api.blogs.MessageType.POST;
import static org.briarproject.api.identity.Author.Status.VERIFIED;
import static org.briarproject.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.blogs.BlogManagerImpl.CLIENT_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BlogManagerImplTest extends BriarTestCase {

	private final Mockery context = new Mockery();
	private final BlogManagerImpl blogManager;
	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final IdentityManager identityManager =
			context.mock(IdentityManager.class);
	private final ClientHelper clientHelper = context.mock(ClientHelper.class);
	private final ContactManager contactManager =
			context.mock(ContactManager.class);
	private final BlogFactory blogFactory = context.mock(BlogFactory.class);

	private final Blog blog1, blog2;
	private final Message message;
	private final MessageId messageId;

	@Inject
	@SuppressWarnings("WeakerAccess")
	BlogPostFactory blogPostFactory;

	public BlogManagerImplTest() {
		MetadataParser metadataParser = context.mock(MetadataParser.class);
		blogManager = new BlogManagerImpl(db, identityManager, clientHelper,
				metadataParser, contactManager, blogFactory, blogPostFactory);

		blog1 = createBlog();
		blog2 = createBlog();
		messageId = new MessageId(getRandomId());
		message = new Message(messageId, blog1.getId(), 42, getRandomBytes(42));
	}

	@Test
	public void testCreateLocalState() throws DbException {
		final Transaction txn = new Transaction(null, false);

		final ContactId contactId = new ContactId(0);
		final Collection<ContactId> contactIds =
				Collections.singletonList(contactId);

		Contact contact = new Contact(contactId, blog2.getAuthor(),
				blog1.getAuthor().getId(), true, true);
		final Collection<Contact> contacts = Collections.singletonList(contact);

		context.checking(new Expectations() {{
			oneOf(identityManager).getLocalAuthor(txn);
			will(returnValue(blog1.getAuthor()));
			oneOf(blogFactory).createBlog(blog1.getAuthor());
			will(returnValue(blog1));
			oneOf(db).containsGroup(txn, blog1.getId());
			will(returnValue(false));
			oneOf(db).addGroup(txn, blog1.getGroup());
			oneOf(db).getContacts(txn, blog1.getAuthor().getId());
			will(returnValue(contactIds));
			oneOf(db).setVisibleToContact(txn, contactId, blog1.getId(), true);
			oneOf(db).getContacts(txn);
			will(returnValue(contacts));
			oneOf(blogFactory).createBlog(blog2.getAuthor());
			will(returnValue(blog2));
			oneOf(db).containsGroup(txn, blog2.getId());
			will(returnValue(false));
			oneOf(db).addGroup(txn, blog2.getGroup());
			oneOf(db).setVisibleToContact(txn, contactId, blog2.getId(), true);
			oneOf(db).getLocalAuthor(txn, blog1.getAuthor().getId());
			will(returnValue(blog1.getAuthor()));
			oneOf(blogFactory).createBlog(blog1.getAuthor());
			will(returnValue(blog1));
			oneOf(db).setVisibleToContact(txn, contactId, blog1.getId(), true);
		}});

		blogManager.createLocalState(txn);
		context.assertIsSatisfied();
	}

	@Test
	public void testRemovingContact() throws DbException {
		final Transaction txn = new Transaction(null, false);

		final ContactId contactId = new ContactId(0);
		Contact contact = new Contact(contactId, blog2.getAuthor(),
				blog1.getAuthor().getId(), true, true);

		context.checking(new Expectations() {{
			oneOf(blogFactory).createBlog(blog2.getAuthor());
			will(returnValue(blog2));
			oneOf(db).removeGroup(txn, blog2.getGroup());
		}});

		blogManager.removingContact(txn, contact);
		context.assertIsSatisfied();
	}

	@Test
	public void testIncomingMessage() throws DbException, FormatException {
		final Transaction txn = new Transaction(null, false);
		BdfList list = new BdfList();
		BdfDictionary author = authorToBdfDictionary(blog1.getAuthor());
		BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(KEY_TYPE, POST.getInt()),
				new BdfEntry(KEY_TIMESTAMP, 0),
				new BdfEntry(KEY_TIME_RECEIVED, 1),
				new BdfEntry(KEY_AUTHOR, author),
				new BdfEntry(KEY_READ, false)
		);

		context.checking(new Expectations() {{
			oneOf(identityManager)
					.getAuthorStatus(txn, blog1.getAuthor().getId());
			will(returnValue(VERIFIED));
		}});

		blogManager.incomingMessage(txn, message, list, meta);
		context.assertIsSatisfied();

		assertEquals(1, txn.getEvents().size());
		assertTrue(txn.getEvents().get(0) instanceof BlogPostAddedEvent);

		BlogPostAddedEvent e = (BlogPostAddedEvent) txn.getEvents().get(0);
		assertEquals(blog1.getId(), e.getGroupId());

		BlogPostHeader h = e.getHeader();
		assertEquals(1, h.getTimeReceived());
		assertEquals(messageId, h.getId());
		assertEquals(null, h.getParentId());
		assertEquals(VERIFIED, h.getAuthorStatus());
		assertEquals(blog1.getAuthor(), h.getAuthor());
	}

	@Test
	public void testRemoveBlog() throws Exception {
		final Transaction txn = new Transaction(null, false);

		checkGetBlogExpectations(txn, false, blog1);
		context.checking(new Expectations() {{
			oneOf(identityManager).getLocalAuthor(txn);
			will(returnValue(blog2.getAuthor()));
			oneOf(contactManager).contactExists(txn, blog1.getAuthor().getId(),
					blog2.getAuthor().getId());
			will(returnValue(false));
			oneOf(db).removeGroup(txn, blog1.getGroup());
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		blogManager.removeBlog(blog1);
		context.assertIsSatisfied();
	}

	@Test
	public void testAddLocalPost() throws DbException, FormatException {
		final Transaction txn = new Transaction(null, false);
		final BlogPost post =
				new BlogPost(message, null, blog1.getAuthor());
		BdfDictionary authorMeta = authorToBdfDictionary(blog1.getAuthor());
		final BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(KEY_TYPE, POST.getInt()),
				new BdfEntry(KEY_TIMESTAMP, message.getTimestamp()),
				new BdfEntry(KEY_AUTHOR, authorMeta),
				new BdfEntry(KEY_READ, true)
		);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(clientHelper)
					.addLocalMessage(txn, message, meta, true);
			oneOf(identityManager)
					.getAuthorStatus(txn, blog1.getAuthor().getId());
			will(returnValue(VERIFIED));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		blogManager.addLocalPost(post);
		context.assertIsSatisfied();

		assertEquals(1, txn.getEvents().size());
		assertTrue(txn.getEvents().get(0) instanceof BlogPostAddedEvent);

		BlogPostAddedEvent e = (BlogPostAddedEvent) txn.getEvents().get(0);
		assertEquals(blog1.getId(), e.getGroupId());

		BlogPostHeader h = e.getHeader();
		assertEquals(message.getTimestamp(), h.getTimeReceived());
		assertEquals(messageId, h.getId());
		assertEquals(null, h.getParentId());
		assertEquals(VERIFIED, h.getAuthorStatus());
		assertEquals(blog1.getAuthor(), h.getAuthor());
	}

	@Test
	public void testBlogCanBeRemoved() throws Exception {
		// check that own personal blogs can not be removed
		final Transaction txn = new Transaction(null, true);
		checkGetBlogExpectations(txn, true, blog1);
		context.checking(new Expectations() {{
			oneOf(identityManager).getLocalAuthor(txn);
			will(returnValue(blog1.getAuthor()));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});
		assertFalse(blogManager.canBeRemoved(blog1.getId()));
		context.assertIsSatisfied();

		// check that blogs of contacts can not be removed
		final Transaction txn2 = new Transaction(null, true);
		checkGetBlogExpectations(txn2, true, blog1);
		context.checking(new Expectations() {{
			oneOf(identityManager).getLocalAuthor(txn2);
			will(returnValue(blog2.getAuthor()));
			oneOf(contactManager).contactExists(txn2, blog1.getAuthor().getId(),
					blog2.getAuthor().getId());
			will(returnValue(true));
			oneOf(db).commitTransaction(txn2);
			oneOf(db).endTransaction(txn2);
		}});
		assertFalse(blogManager.canBeRemoved(blog1.getId()));
		context.assertIsSatisfied();

		// check that blogs can be removed if they don't belong to a contact
		final Transaction txn3 = new Transaction(null, true);
		checkGetBlogExpectations(txn3, true, blog1);
		context.checking(new Expectations() {{
			oneOf(identityManager).getLocalAuthor(txn3);
			will(returnValue(blog2.getAuthor()));
			oneOf(contactManager).contactExists(txn3, blog1.getAuthor().getId(),
					blog2.getAuthor().getId());
			will(returnValue(false));
			oneOf(db).commitTransaction(txn3);
			oneOf(db).endTransaction(txn3);
		}});
		assertTrue(blogManager.canBeRemoved(blog1.getId()));
		context.assertIsSatisfied();
	}

	private void checkGetBlogExpectations(final Transaction txn,
			final boolean readOnly, final Blog blog) throws Exception {
		context.checking(new Expectations() {{
			oneOf(db).startTransaction(readOnly);
			will(returnValue(txn));
			oneOf(db).getGroup(txn, blog.getId());
			will(returnValue(blog.getGroup()));
			oneOf(blogFactory).parseBlog(blog.getGroup());
			will(returnValue(blog));
		}});
	}

	private Blog createBlog() {
		final GroupId groupId = new GroupId(getRandomId());
		final Group group = new Group(groupId, CLIENT_ID, getRandomBytes(42));
		final AuthorId authorId = new AuthorId(getRandomId());
		final byte[] publicKey = getRandomBytes(MAX_PUBLIC_KEY_LENGTH);
		final byte[] privateKey = getRandomBytes(MAX_PUBLIC_KEY_LENGTH);
		final long created = System.currentTimeMillis();
		final LocalAuthor localAuthor =
				new LocalAuthor(authorId, "Author", publicKey, privateKey,
						created);
		return new Blog(group, localAuthor);
	}

	private BdfDictionary authorToBdfDictionary(Author a) {
		return BdfDictionary.of(
				new BdfEntry(KEY_AUTHOR_ID, a.getId()),
				new BdfEntry(KEY_AUTHOR_NAME, a.getName()),
				new BdfEntry(KEY_PUBLIC_KEY, a.getPublicKey())
		);
	}

}
