package org.briarproject.blogs;

import org.briarproject.BriarTestCase;
import org.briarproject.api.FormatException;
import org.briarproject.api.blogs.Blog;
import org.briarproject.api.blogs.BlogFactory;
import org.briarproject.api.blogs.BlogPost;
import org.briarproject.api.blogs.BlogPostHeader;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
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

import static org.briarproject.TestUtils.getRandomBytes;
import static org.briarproject.TestUtils.getRandomId;
import static org.briarproject.api.blogs.BlogConstants.KEY_AUTHOR;
import static org.briarproject.api.blogs.BlogConstants.KEY_AUTHOR_ID;
import static org.briarproject.api.blogs.BlogConstants.KEY_AUTHOR_NAME;
import static org.briarproject.api.blogs.BlogConstants.KEY_CONTENT_TYPE;
import static org.briarproject.api.blogs.BlogConstants.KEY_DESCRIPTION;
import static org.briarproject.api.blogs.BlogConstants.KEY_PUBLIC_KEY;
import static org.briarproject.api.blogs.BlogConstants.KEY_READ;
import static org.briarproject.api.blogs.BlogConstants.KEY_TIMESTAMP;
import static org.briarproject.api.blogs.BlogConstants.KEY_TIME_RECEIVED;
import static org.briarproject.api.identity.Author.Status.VERIFIED;
import static org.briarproject.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.blogs.BlogManagerImpl.CLIENT_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BlogManagerImplTest extends BriarTestCase {

	private final Mockery context = new Mockery();
	private final BlogManagerImpl blogManager;
	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final IdentityManager identityManager =
			context.mock(IdentityManager.class);
	private final ClientHelper clientHelper = context.mock(ClientHelper.class);
	private final MetadataParser metadataParser =
			context.mock(MetadataParser.class);
	private final BlogFactory blogFactory = context.mock(BlogFactory.class);

	private final Blog blog1, blog2;
	private final Message message;
	private final MessageId messageId;

	public BlogManagerImplTest() {
		blogManager = new BlogManagerImpl(db, identityManager, clientHelper,
				metadataParser, blogFactory);

		blog1 = getBlog("Test Blog 1", "Test Description 1");
		blog2 = getBlog("Test Blog 2", "Test Description 2");
		messageId = new MessageId(getRandomId());
		message = new Message(messageId, blog1.getId(), 42, getRandomBytes(42));
	}

	@Test
	public void testClientId() {
		assertEquals(CLIENT_ID, blogManager.getClientId());
	}

	@Test
	public void testCreateLocalState() throws DbException {
		final Transaction txn = new Transaction(null, false);
		final Collection<LocalAuthor> localAuthors =
				Collections.singletonList((LocalAuthor) blog1.getAuthor());

		final ContactId contactId = new ContactId(0);
		final Collection<ContactId> contactIds =
				Collections.singletonList(contactId);

		Contact contact = new Contact(contactId, blog2.getAuthor(),
				blog1.getAuthor().getId(), true);
		final Collection<Contact> contacts = Collections.singletonList(contact);

		context.checking(new Expectations() {{
			oneOf(db).getLocalAuthors(txn);
			will(returnValue(localAuthors));
			oneOf(blogFactory).createPersonalBlog(blog1.getAuthor());
			will(returnValue(blog1));
			oneOf(db).containsGroup(txn, blog1.getId());
			will(returnValue(false));
			oneOf(db).addGroup(txn, blog1.getGroup());
			oneOf(db).getContacts(txn, blog1.getAuthor().getId());
			will(returnValue(contactIds));
			oneOf(db).setVisibleToContact(txn, contactId, blog1.getId(), true);
			oneOf(db).getContacts(txn);
			will(returnValue(contacts));
			oneOf(blogFactory).createPersonalBlog(blog2.getAuthor());
			will(returnValue(blog2));
			oneOf(db).containsGroup(txn, blog2.getId());
			will(returnValue(false));
			oneOf(db).addGroup(txn, blog2.getGroup());
			oneOf(db).setVisibleToContact(txn, contactId, blog2.getId(), true);
			oneOf(db).getLocalAuthor(txn, blog1.getAuthor().getId());
			will(returnValue(blog1.getAuthor()));
			oneOf(blogFactory).createPersonalBlog(blog1.getAuthor());
			will(returnValue(blog1));
			oneOf(db).setVisibleToContact(txn, contactId, blog1.getId(), true);
		}});

		blogManager.createLocalState(txn);
	}

	@Test
	public void testRemovingContact() throws DbException {
		final Transaction txn = new Transaction(null, false);

		final ContactId contactId = new ContactId(0);
		Contact contact = new Contact(contactId, blog2.getAuthor(),
				blog1.getAuthor().getId(), true);

		context.checking(new Expectations() {{
			oneOf(blogFactory).createPersonalBlog(blog2.getAuthor());
			will(returnValue(blog2));
			oneOf(db).removeGroup(txn, blog2.getGroup());
		}});

		blogManager.removingContact(txn, contact);
	}

	@Test
	public void testAddingIdentity() throws DbException {
		final Transaction txn = new Transaction(null, false);
		Author a = blog1.getAuthor();
		final LocalAuthor localAuthor =
				new LocalAuthor(a.getId(), a.getName(), a.getPublicKey(),
						a.getPublicKey(), 0);

		context.checking(new Expectations() {{
			oneOf(blogFactory).createPersonalBlog(localAuthor);
			will(returnValue(blog1));
			oneOf(db).addGroup(txn, blog1.getGroup());
		}});

		blogManager.addingIdentity(txn, localAuthor);
	}

	@Test
	public void testRemovingIdentity() throws DbException {
		final Transaction txn = new Transaction(null, false);
		Author a = blog1.getAuthor();
		final LocalAuthor localAuthor =
				new LocalAuthor(a.getId(), a.getName(), a.getPublicKey(),
						a.getPublicKey(), 0);

		context.checking(new Expectations() {{
			oneOf(blogFactory).createPersonalBlog(localAuthor);
			will(returnValue(blog1));
			oneOf(db).removeGroup(txn, blog1.getGroup());
		}});

		blogManager.removingIdentity(txn, localAuthor);
	}

	@Test
	public void testIncomingMessage() throws DbException, FormatException {
		final Transaction txn = new Transaction(null, false);
		BdfList list = new BdfList();
		BdfDictionary author = authorToBdfDictionary(blog1.getAuthor());
		BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(KEY_TIMESTAMP, 0),
				new BdfEntry(KEY_TIME_RECEIVED, 1),
				new BdfEntry(KEY_AUTHOR, author),
				new BdfEntry(KEY_CONTENT_TYPE, 0),
				new BdfEntry(KEY_READ, false),
				new BdfEntry(KEY_CONTENT_TYPE, "text/plain")
		);

		context.checking(new Expectations() {{
			oneOf(identityManager)
					.getAuthorStatus(txn, blog1.getAuthor().getId());
			will(returnValue(VERIFIED));
		}});

		blogManager.incomingMessage(txn, message, list, meta);

		assertEquals(1, txn.getEvents().size());
		assertTrue(txn.getEvents().get(0) instanceof BlogPostAddedEvent);

		BlogPostAddedEvent e = (BlogPostAddedEvent) txn.getEvents().get(0);
		assertEquals(blog1.getId(), e.getGroupId());

		BlogPostHeader h = e.getHeader();
		assertEquals(1, h.getTimeReceived());
		assertEquals(messageId, h.getId());
		assertEquals(null, h.getParentId());
		assertEquals(VERIFIED, h.getAuthorStatus());
		assertEquals("text/plain", h.getContentType());
		assertEquals(blog1.getAuthor(), h.getAuthor());
	}

	@Test
	public void testAddBlog() throws DbException, FormatException {
		final Transaction txn = new Transaction(null, false);
		Author a = blog1.getAuthor();
		final LocalAuthor localAuthor =
				new LocalAuthor(a.getId(), a.getName(), a.getPublicKey(),
						a.getPublicKey(), 0);
		final BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(KEY_DESCRIPTION, blog1.getDescription())
		);

		context.checking(new Expectations() {{
			oneOf(blogFactory)
					.createBlog(blog1.getName(), blog1.getDescription(),
							blog1.getAuthor());
			will(returnValue(blog1));
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(db).addGroup(txn, blog1.getGroup());
			oneOf(clientHelper).mergeGroupMetadata(txn, blog1.getId(), meta);
			oneOf(db).endTransaction(txn);
		}});

		blogManager
				.addBlog(localAuthor, blog1.getName(), blog1.getDescription());
		assertTrue(txn.isComplete());
	}

	@Test
	public void testRemoveBlog() throws DbException, FormatException {
		final Transaction txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(db).removeGroup(txn, blog1.getGroup());
			oneOf(db).endTransaction(txn);
		}});

		blogManager.removeBlog(blog1);
		assertTrue(txn.isComplete());
	}

	@Test
	public void testAddLocalPost() throws DbException, FormatException {
		final Transaction txn = new Transaction(null, true);
		final BlogPost post =
				new BlogPost(null, message, null, blog1.getAuthor(),
						"text/plain");
		BdfDictionary authorMeta = authorToBdfDictionary(blog1.getAuthor());
		final BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(KEY_TIMESTAMP, message.getTimestamp()),
				new BdfEntry(KEY_AUTHOR, authorMeta),
				new BdfEntry(KEY_CONTENT_TYPE, "text/plain"),
				new BdfEntry(KEY_READ, true)
		);

		context.checking(new Expectations() {{
			oneOf(clientHelper).addLocalMessage(message, CLIENT_ID, meta, true);
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(identityManager)
					.getAuthorStatus(txn, blog1.getAuthor().getId());
			will(returnValue(VERIFIED));
			oneOf(db).endTransaction(txn);
		}});

		blogManager.addLocalPost(post);
		assertTrue(txn.isComplete());

		assertEquals(1, txn.getEvents().size());
		assertTrue(txn.getEvents().get(0) instanceof BlogPostAddedEvent);

		BlogPostAddedEvent e = (BlogPostAddedEvent) txn.getEvents().get(0);
		assertEquals(blog1.getId(), e.getGroupId());

		BlogPostHeader h = e.getHeader();
		assertEquals(message.getTimestamp(), h.getTimeReceived());
		assertEquals(messageId, h.getId());
		assertEquals(null, h.getParentId());
		assertEquals(VERIFIED, h.getAuthorStatus());
		assertEquals("text/plain", h.getContentType());
		assertEquals(blog1.getAuthor(), h.getAuthor());
	}

	private Blog getBlog(String name, String desc) {
		final GroupId groupId = new GroupId(getRandomId());
		final Group group = new Group(groupId, CLIENT_ID, getRandomBytes(42));
		final AuthorId authorId = new AuthorId(getRandomId());
		final byte[] publicKey = getRandomBytes(MAX_PUBLIC_KEY_LENGTH);
		final byte[] privateKey = getRandomBytes(MAX_PUBLIC_KEY_LENGTH);
		final long created = System.currentTimeMillis();
		final LocalAuthor localAuthor =
				new LocalAuthor(authorId, "Author", publicKey, privateKey,
						created);
		return new Blog(group, name, desc, localAuthor, false);
	}

	private BdfDictionary authorToBdfDictionary(Author a) {
		return BdfDictionary.of(
				new BdfEntry(KEY_AUTHOR_ID, a.getId()),
				new BdfEntry(KEY_AUTHOR_NAME, a.getName()),
				new BdfEntry(KEY_PUBLIC_KEY, a.getPublicKey())
		);
	}

}
