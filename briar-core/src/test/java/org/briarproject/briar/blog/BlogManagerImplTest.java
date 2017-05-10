package org.briarproject.briar.blog;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.blog.Blog;
import org.briarproject.briar.api.blog.BlogFactory;
import org.briarproject.briar.api.blog.BlogPost;
import org.briarproject.briar.api.blog.BlogPostFactory;
import org.briarproject.briar.api.blog.BlogPostHeader;
import org.briarproject.briar.api.blog.event.BlogPostAddedEvent;
import org.briarproject.briar.test.BriarTestCase;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;

import static org.briarproject.bramble.api.identity.Author.Status.VERIFIED;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.bramble.api.sync.Group.Visibility.SHARED;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_AUTHOR;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_AUTHOR_ID;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_AUTHOR_NAME;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_PUBLIC_KEY;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_READ;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_RSS_FEED;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_TIMESTAMP;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_TIME_RECEIVED;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_TYPE;
import static org.briarproject.briar.api.blog.BlogManager.CLIENT_ID;
import static org.briarproject.briar.api.blog.MessageType.POST;
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
	private final BlogFactory blogFactory = context.mock(BlogFactory.class);

	private final Blog blog1, blog2;
	private final Message message;
	private final MessageId messageId;

	public BlogManagerImplTest() {
		MetadataParser metadataParser = context.mock(MetadataParser.class);
		BlogPostFactory blogPostFactory = context.mock(BlogPostFactory.class);
		blogManager = new BlogManagerImpl(db, identityManager, clientHelper,
				metadataParser, blogFactory, blogPostFactory);

		blog1 = createBlog();
		blog2 = createBlog();
		messageId = new MessageId(getRandomId());
		message = new Message(messageId, blog1.getId(), 42, getRandomBytes(42));
	}

	@Test
	public void testCreateLocalState() throws DbException {
		final Transaction txn = new Transaction(null, false);

		final ContactId contactId = new ContactId(0);

		Contact contact = new Contact(contactId, blog2.getAuthor(),
				blog1.getAuthor().getId(), true, true);
		final Collection<Contact> contacts = Collections.singletonList(contact);

		context.checking(new Expectations() {{
			oneOf(identityManager).getLocalAuthor(txn);
			will(returnValue(blog1.getAuthor()));
			oneOf(blogFactory).createBlog(blog1.getAuthor());
			will(returnValue(blog1));
			oneOf(db).addGroup(txn, blog1.getGroup());
			oneOf(db).getContacts(txn);
			will(returnValue(contacts));
			oneOf(blogFactory).createBlog(blog2.getAuthor());
			will(returnValue(blog2));
			oneOf(db).addGroup(txn, blog2.getGroup());
			oneOf(db).setGroupVisibility(txn, contactId, blog2.getId(), SHARED);
			oneOf(identityManager).getLocalAuthor(txn);
			will(returnValue(blog1.getAuthor()));
			oneOf(blogFactory).createBlog(blog1.getAuthor());
			will(returnValue(blog1));
			oneOf(db).setGroupVisibility(txn, contactId, blog1.getId(), SHARED);
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
			oneOf(identityManager).getLocalAuthor(txn);
			will(returnValue(blog1.getAuthor()));
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

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(identityManager).getLocalAuthor(txn);
			will(returnValue(blog2.getAuthor()));
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
				new BdfEntry(KEY_READ, true),
				new BdfEntry(KEY_RSS_FEED, false)
		);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(db).getGroup(txn, blog1.getId());
			will(returnValue(blog1.getGroup()));
			oneOf(blogFactory).parseBlog(blog1.getGroup());
			will(returnValue(blog1));
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
		context.checking(new Expectations() {{
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(identityManager).getLocalAuthor(txn);
			will(returnValue(blog1.getAuthor()));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});
		assertFalse(blogManager.canBeRemoved(blog1));
		context.assertIsSatisfied();

		// check that blogs of contacts can be removed
		final Transaction txn2 = new Transaction(null, true);
		context.checking(new Expectations() {{
			oneOf(db).startTransaction(true);
			will(returnValue(txn2));
			oneOf(identityManager).getLocalAuthor(txn2);
			will(returnValue(blog2.getAuthor()));
			oneOf(db).commitTransaction(txn2);
			oneOf(db).endTransaction(txn2);
		}});
		assertTrue(blogManager.canBeRemoved(blog1));
		context.assertIsSatisfied();
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
		return new Blog(group, localAuthor, false);
	}

	private BdfDictionary authorToBdfDictionary(Author a) {
		return BdfDictionary.of(
				new BdfEntry(KEY_AUTHOR_ID, a.getId()),
				new BdfEntry(KEY_AUTHOR_NAME, a.getName()),
				new BdfEntry(KEY_PUBLIC_KEY, a.getPublicKey())
		);
	}

}
