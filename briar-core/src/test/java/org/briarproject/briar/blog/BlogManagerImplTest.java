package org.briarproject.briar.blog;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.CommitAction;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.EventAction;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.blog.Blog;
import org.briarproject.briar.api.blog.BlogCommentHeader;
import org.briarproject.briar.api.blog.BlogFactory;
import org.briarproject.briar.api.blog.BlogPost;
import org.briarproject.briar.api.blog.BlogPostFactory;
import org.briarproject.briar.api.blog.BlogPostHeader;
import org.briarproject.briar.api.blog.event.BlogPostAddedEvent;
import org.briarproject.briar.api.identity.AuthorInfo;
import org.briarproject.briar.api.identity.AuthorManager;
import org.briarproject.briar.test.BriarTestCase;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import static org.briarproject.bramble.api.sync.validation.IncomingMessageHook.DeliveryAction.ACCEPT_SHARE;
import static org.briarproject.bramble.test.TestUtils.getContact;
import static org.briarproject.bramble.test.TestUtils.getGroup;
import static org.briarproject.bramble.test.TestUtils.getLocalAuthor;
import static org.briarproject.bramble.test.TestUtils.getMessage;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_AUTHOR;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_COMMENT;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_ORIGINAL_MSG_ID;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_ORIGINAL_PARENT_MSG_ID;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_PARENT_MSG_ID;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_READ;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_RSS_FEED;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_TIMESTAMP;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_TIME_RECEIVED;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_TYPE;
import static org.briarproject.briar.api.blog.BlogConstants.MAX_BLOG_COMMENT_TEXT_LENGTH;
import static org.briarproject.briar.api.blog.BlogManager.CLIENT_ID;
import static org.briarproject.briar.api.blog.BlogManager.MAJOR_VERSION;
import static org.briarproject.briar.api.blog.MessageType.COMMENT;
import static org.briarproject.briar.api.blog.MessageType.POST;
import static org.briarproject.briar.api.blog.MessageType.WRAPPED_COMMENT;
import static org.briarproject.briar.api.blog.MessageType.WRAPPED_POST;
import static org.briarproject.briar.api.identity.AuthorInfo.Status.NONE;
import static org.briarproject.briar.api.identity.AuthorInfo.Status.OURSELVES;
import static org.briarproject.briar.api.identity.AuthorInfo.Status.VERIFIED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BlogManagerImplTest extends BriarTestCase {

	private final Mockery context = new Mockery();
	private final BlogManagerImpl blogManager;
	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final AuthorManager authorManager =
			context.mock(AuthorManager.class);
	private final IdentityManager identityManager =
			context.mock(IdentityManager.class);
	private final ClientHelper clientHelper = context.mock(ClientHelper.class);
	private final BlogFactory blogFactory = context.mock(BlogFactory.class);
	private final BlogPostFactory blogPostFactory =
			context.mock(BlogPostFactory.class);

	private final LocalAuthor localAuthor1, localAuthor2, rssLocalAuthor;
	private final AuthorInfo ourselvesInfo = new AuthorInfo(OURSELVES);
	private final AuthorInfo verifiedInfo = new AuthorInfo(VERIFIED);
	private final BdfList authorList1, authorList2, rssAuthorList;
	private final Blog blog1, blog2, rssBlog;
	private final Message message, rssMessage;
	private final MessageId messageId, rssMessageId;
	private final long timestamp, timeReceived;
	private final String comment;

	public BlogManagerImplTest() {
		MetadataParser metadataParser = context.mock(MetadataParser.class);
		blogManager = new BlogManagerImpl(db, identityManager, authorManager,
				clientHelper, metadataParser, blogFactory, blogPostFactory);

		localAuthor1 = getLocalAuthor();
		localAuthor2 = getLocalAuthor();
		rssLocalAuthor = getLocalAuthor();
		authorList1 = authorToBdfList(localAuthor1);
		authorList2 = authorToBdfList(localAuthor2);
		rssAuthorList = authorToBdfList(rssLocalAuthor);
		blog1 = createBlog(localAuthor1, false);
		blog2 = createBlog(localAuthor2, false);
		rssBlog = createBlog(rssLocalAuthor, true);
		message = getMessage(blog1.getId());
		rssMessage = getMessage(rssBlog.getId());
		messageId = message.getId();
		rssMessageId = rssMessage.getId();
		timestamp = message.getTimestamp();
		timeReceived = timestamp + 1;
		comment = getRandomString(MAX_BLOG_COMMENT_TEXT_LENGTH);
	}

	@Test
	public void testOpenDatabaseHook() throws DbException {
		Transaction txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(identityManager).getLocalAuthor(txn);
			will(returnValue(blog1.getAuthor()));
			oneOf(blogFactory).createBlog(blog1.getAuthor());
			will(returnValue(blog1));
			oneOf(db).addGroup(txn, blog1.getGroup());
		}});

		blogManager.onDatabaseOpened(txn);
		context.assertIsSatisfied();
	}

	@Test
	public void testRemovingContact() throws DbException {
		Transaction txn = new Transaction(null, false);
		Contact contact = getContact(blog2.getAuthor(),
				blog1.getAuthor().getId(), true);

		context.checking(new Expectations() {{
			oneOf(blogFactory).createBlog(blog2.getAuthor());
			will(returnValue(blog2));
			oneOf(db).containsGroup(txn, blog2.getId());
			will(returnValue(true));
			oneOf(identityManager).getLocalAuthor(txn);
			will(returnValue(blog1.getAuthor()));
			oneOf(db).removeGroup(txn, blog2.getGroup());
		}});

		blogManager.removingContact(txn, contact);
		context.assertIsSatisfied();
	}

	@Test
	public void testRemovingContactAfterRemovingBlog() throws DbException {
		Transaction txn = new Transaction(null, false);
		Contact contact = getContact(blog2.getAuthor(),
				blog1.getAuthor().getId(), true);

		context.checking(new Expectations() {{
			oneOf(blogFactory).createBlog(blog2.getAuthor());
			will(returnValue(blog2));
			oneOf(db).containsGroup(txn, blog2.getId());
			will(returnValue(false));
		}});

		blogManager.removingContact(txn, contact);
		context.assertIsSatisfied();
	}

	@Test
	public void testIncomingMessage() throws DbException, FormatException {
		Transaction txn = new Transaction(null, false);
		BdfList body = BdfList.of("body");
		BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(KEY_TYPE, POST.getInt()),
				new BdfEntry(KEY_TIMESTAMP, timestamp),
				new BdfEntry(KEY_TIME_RECEIVED, timeReceived),
				new BdfEntry(KEY_AUTHOR, authorList1),
				new BdfEntry(KEY_READ, false),
				new BdfEntry(KEY_RSS_FEED, false)
		);

		context.checking(new Expectations() {{
			oneOf(clientHelper).parseAndValidateAuthor(authorList1);
			will(returnValue(localAuthor1));
			oneOf(authorManager).getAuthorInfo(txn, localAuthor1.getId());
			will(returnValue(verifiedInfo));
		}});

		assertEquals(ACCEPT_SHARE,
				blogManager.incomingMessage(txn, message, body, meta));
		context.assertIsSatisfied();

		assertEquals(1, txn.getActions().size());
		CommitAction action = txn.getActions().get(0);
		assertTrue(action instanceof EventAction);
		Event event = ((EventAction) action).getEvent();
		assertTrue(event instanceof BlogPostAddedEvent);
		BlogPostAddedEvent e = (BlogPostAddedEvent) event;
		assertEquals(blog1.getId(), e.getGroupId());

		BlogPostHeader h = e.getHeader();
		assertEquals(POST, h.getType());
		assertFalse(h.isRssFeed());
		assertEquals(timestamp, h.getTimestamp());
		assertEquals(timeReceived, h.getTimeReceived());
		assertEquals(messageId, h.getId());
		assertEquals(blog1.getId(), h.getGroupId());
		assertNull(h.getParentId());
		assertEquals(VERIFIED, h.getAuthorStatus());
		assertEquals(localAuthor1, h.getAuthor());
	}

	@Test
	public void testIncomingRssMessage() throws DbException, FormatException {
		Transaction txn = new Transaction(null, false);
		BdfList body = BdfList.of("body");
		BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(KEY_TYPE, POST.getInt()),
				new BdfEntry(KEY_TIMESTAMP, timestamp),
				new BdfEntry(KEY_TIME_RECEIVED, timeReceived),
				new BdfEntry(KEY_AUTHOR, rssAuthorList),
				new BdfEntry(KEY_READ, false),
				new BdfEntry(KEY_RSS_FEED, true)
		);

		context.checking(new Expectations() {{
			oneOf(clientHelper).parseAndValidateAuthor(rssAuthorList);
			will(returnValue(rssLocalAuthor));
		}});

		assertEquals(ACCEPT_SHARE,
				blogManager.incomingMessage(txn, rssMessage, body, meta));
		context.assertIsSatisfied();

		assertEquals(1, txn.getActions().size());
		CommitAction action = txn.getActions().get(0);
		assertTrue(action instanceof EventAction);
		Event event = ((EventAction) action).getEvent();
		assertTrue(event instanceof BlogPostAddedEvent);
		BlogPostAddedEvent e = (BlogPostAddedEvent) event;
		assertEquals(rssBlog.getId(), e.getGroupId());

		BlogPostHeader h = e.getHeader();
		assertEquals(POST, h.getType());
		assertTrue(h.isRssFeed());
		assertEquals(timestamp, h.getTimestamp());
		assertEquals(timeReceived, h.getTimeReceived());
		assertEquals(rssMessageId, h.getId());
		assertEquals(rssBlog.getId(), h.getGroupId());
		assertNull(h.getParentId());
		assertEquals(NONE, h.getAuthorStatus());
		assertEquals(rssLocalAuthor, h.getAuthor());
	}

	@Test
	public void testRemoveBlog() throws Exception {
		Transaction txn = new Transaction(null, false);

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
		Transaction txn = new Transaction(null, false);
		BlogPost post = new BlogPost(message, null, localAuthor1);
		BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(KEY_TYPE, POST.getInt()),
				new BdfEntry(KEY_TIMESTAMP, timestamp),
				new BdfEntry(KEY_AUTHOR, authorList1),
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
			oneOf(clientHelper).toList(localAuthor1);
			will(returnValue(authorList1));
			oneOf(clientHelper).addLocalMessage(txn, message, meta, true,
					false);
			oneOf(clientHelper).parseAndValidateAuthor(authorList1);
			will(returnValue(localAuthor1));
			oneOf(authorManager).getAuthorInfo(txn, localAuthor1.getId());
			will(returnValue(ourselvesInfo));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		blogManager.addLocalPost(post);
		context.assertIsSatisfied();

		assertEquals(1, txn.getActions().size());
		CommitAction action = txn.getActions().get(0);
		assertTrue(action instanceof EventAction);
		Event event = ((EventAction) action).getEvent();
		assertTrue(event instanceof BlogPostAddedEvent);
		BlogPostAddedEvent e = (BlogPostAddedEvent) event;
		assertEquals(blog1.getId(), e.getGroupId());
		assertTrue(e.isLocal());

		BlogPostHeader h = e.getHeader();
		assertEquals(POST, h.getType());
		assertEquals(timestamp, h.getTimestamp());
		assertEquals(timestamp, h.getTimeReceived());
		assertEquals(messageId, h.getId());
		assertEquals(blog1.getId(), h.getGroupId());
		assertNull(h.getParentId());
		assertEquals(OURSELVES, h.getAuthorStatus());
		assertEquals(localAuthor1, h.getAuthor());
	}

	@Test
	public void testAddLocalRssPost() throws DbException, FormatException {
		Transaction txn = new Transaction(null, false);
		BlogPost post = new BlogPost(rssMessage, null, rssLocalAuthor);
		BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(KEY_TYPE, POST.getInt()),
				new BdfEntry(KEY_TIMESTAMP, timestamp),
				new BdfEntry(KEY_AUTHOR, rssAuthorList),
				new BdfEntry(KEY_READ, true),
				new BdfEntry(KEY_RSS_FEED, true)
		);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(db).getGroup(txn, rssBlog.getId());
			will(returnValue(rssBlog.getGroup()));
			oneOf(blogFactory).parseBlog(rssBlog.getGroup());
			will(returnValue(rssBlog));
			oneOf(clientHelper).toList(rssLocalAuthor);
			will(returnValue(rssAuthorList));
			oneOf(clientHelper).addLocalMessage(txn, rssMessage, meta, true,
					false);
			oneOf(clientHelper).parseAndValidateAuthor(rssAuthorList);
			will(returnValue(rssLocalAuthor));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		blogManager.addLocalPost(post);
		context.assertIsSatisfied();

		assertEquals(1, txn.getActions().size());
		CommitAction action = txn.getActions().get(0);
		assertTrue(action instanceof EventAction);
		Event event = ((EventAction) action).getEvent();
		assertTrue(event instanceof BlogPostAddedEvent);
		BlogPostAddedEvent e = (BlogPostAddedEvent) event;
		assertEquals(rssBlog.getId(), e.getGroupId());
		assertFalse(e.isLocal());

		BlogPostHeader h = e.getHeader();
		assertEquals(POST, h.getType());
		assertTrue(h.isRssFeed());
		assertEquals(timestamp, h.getTimestamp());
		assertEquals(timestamp, h.getTimeReceived());
		assertEquals(rssMessageId, h.getId());
		assertEquals(rssBlog.getId(), h.getGroupId());
		assertNull(h.getParentId());
		assertEquals(NONE, h.getAuthorStatus());
		assertEquals(rssLocalAuthor, h.getAuthor());
	}

	@Test
	public void testAddLocalCommentToLocalPost() throws Exception {
		Transaction txn = new Transaction(null, false);
		// The post was originally posted to blog 1, then reblogged to the
		// same blog (commenting on own post)
		BdfDictionary postMeta = BdfDictionary.of(
				new BdfEntry(KEY_TYPE, POST.getInt()),
				new BdfEntry(KEY_RSS_FEED, false),
				new BdfEntry(KEY_ORIGINAL_MSG_ID, messageId),
				new BdfEntry(KEY_AUTHOR, authorList1),
				new BdfEntry(KEY_TIMESTAMP, timestamp),
				new BdfEntry(KEY_TIME_RECEIVED, timeReceived)
		);
		Message commentMsg = getMessage(blog1.getId());
		MessageId commentId = commentMsg.getId();
		BdfDictionary commentMeta = BdfDictionary.of(
				new BdfEntry(KEY_TYPE, COMMENT.getInt()),
				new BdfEntry(KEY_COMMENT, comment),
				new BdfEntry(KEY_TIMESTAMP, timestamp),
				new BdfEntry(KEY_ORIGINAL_MSG_ID, commentId),
				new BdfEntry(KEY_ORIGINAL_PARENT_MSG_ID, messageId),
				new BdfEntry(KEY_PARENT_MSG_ID, messageId),
				new BdfEntry(KEY_AUTHOR, authorList1)
		);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			// Create the comment
			oneOf(blogPostFactory).createBlogComment(blog1.getId(),
					localAuthor1, comment, messageId, messageId);
			will(returnValue(commentMsg));
			oneOf(clientHelper).toList(localAuthor1);
			will(returnValue(authorList1));
			// Store the comment
			oneOf(clientHelper).addLocalMessage(txn, commentMsg, commentMeta,
					true, false);
			// Create the headers for the comment and its parent
			oneOf(clientHelper).parseAndValidateAuthor(authorList1);
			will(returnValue(localAuthor1));
			oneOf(authorManager).getAuthorInfo(txn, localAuthor1.getId());
			will(returnValue(ourselvesInfo));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn, messageId);
			will(returnValue(postMeta));
			oneOf(clientHelper).parseAndValidateAuthor(authorList1);
			will(returnValue(localAuthor1));
			oneOf(authorManager).getAuthorInfo(txn, localAuthor1.getId());
			will(returnValue(ourselvesInfo));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		BlogPostHeader postHeader = new BlogPostHeader(POST, blog1.getId(),
				messageId, null, timestamp, timeReceived, localAuthor1,
				ourselvesInfo, false, true);
		blogManager.addLocalComment(localAuthor1, blog1.getId(), comment,
				postHeader);
		context.assertIsSatisfied();

		assertEquals(1, txn.getActions().size());
		CommitAction action = txn.getActions().get(0);
		assertTrue(action instanceof EventAction);
		Event event = ((EventAction) action).getEvent();
		assertTrue(event instanceof BlogPostAddedEvent);
		BlogPostAddedEvent e = (BlogPostAddedEvent) event;
		assertEquals(blog1.getId(), e.getGroupId());

		BlogPostHeader h = e.getHeader();
		assertEquals(COMMENT, h.getType());
		assertFalse(h.isRssFeed());
		assertEquals(timestamp, h.getTimestamp());
		assertEquals(timestamp, h.getTimeReceived());
		assertEquals(commentId, h.getId());
		assertEquals(blog1.getId(), h.getGroupId());
		assertEquals(messageId, h.getParentId());
		assertEquals(OURSELVES, h.getAuthorStatus());
		assertEquals(localAuthor1, h.getAuthor());

		assertTrue(h instanceof BlogCommentHeader);
		BlogPostHeader h1 = ((BlogCommentHeader) h).getParent();
		assertEquals(POST, h1.getType());
		assertFalse(h1.isRssFeed());
		assertEquals(timestamp, h1.getTimestamp());
		assertEquals(timeReceived, h1.getTimeReceived());
		assertEquals(messageId, h1.getId());
		assertEquals(blog1.getId(), h.getGroupId());
		assertNull(h1.getParentId());
		assertEquals(OURSELVES, h1.getAuthorStatus());
		assertEquals(localAuthor1, h1.getAuthor());

		assertEquals(h1.getId(), ((BlogCommentHeader) h).getRootPost().getId());
	}

	@Test
	public void testAddLocalCommentToRemotePost() throws Exception {
		Transaction txn = new Transaction(null, false);
		// The post was originally posted to blog 1, then reblogged to
		// blog 2 with a comment
		BdfList originalPostBody = BdfList.of("originalPostBody");
		Message wrappedPostMsg = getMessage(blog2.getId());
		MessageId wrappedPostId = wrappedPostMsg.getId();
		BdfDictionary wrappedPostMeta = BdfDictionary.of(
				new BdfEntry(KEY_TYPE, WRAPPED_POST.getInt()),
				new BdfEntry(KEY_RSS_FEED, false),
				new BdfEntry(KEY_ORIGINAL_MSG_ID, messageId),
				new BdfEntry(KEY_AUTHOR, authorList1),
				new BdfEntry(KEY_TIMESTAMP, timestamp),
				new BdfEntry(KEY_TIME_RECEIVED, timeReceived)
		);
		Message commentMsg = getMessage(blog2.getId());
		MessageId commentId = commentMsg.getId();
		BdfDictionary commentMeta = BdfDictionary.of(
				new BdfEntry(KEY_TYPE, COMMENT.getInt()),
				new BdfEntry(KEY_COMMENT, comment),
				new BdfEntry(KEY_TIMESTAMP, timestamp),
				new BdfEntry(KEY_ORIGINAL_MSG_ID, commentId),
				new BdfEntry(KEY_ORIGINAL_PARENT_MSG_ID, messageId),
				new BdfEntry(KEY_PARENT_MSG_ID, wrappedPostId),
				new BdfEntry(KEY_AUTHOR, authorList2)
		);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			// Wrap the original post for blog 2
			oneOf(clientHelper).getMessageAsList(txn, messageId);
			will(returnValue(originalPostBody));
			oneOf(db).getGroup(txn, blog1.getId());
			will(returnValue(blog1.getGroup()));
			oneOf(blogPostFactory).wrapPost(blog2.getId(),
					blog1.getGroup().getDescriptor(), timestamp,
					originalPostBody);
			will(returnValue(wrappedPostMsg));
			oneOf(clientHelper).toList(localAuthor1);
			will(returnValue(authorList1));
			// Store the wrapped post
			oneOf(clientHelper).addLocalMessage(txn, wrappedPostMsg,
					wrappedPostMeta, true, false);
			// Create the comment
			oneOf(blogPostFactory).createBlogComment(blog2.getId(),
					localAuthor2, comment, messageId, wrappedPostId);
			will(returnValue(commentMsg));
			oneOf(clientHelper).toList(localAuthor2);
			will(returnValue(authorList2));
			// Store the comment
			oneOf(clientHelper).addLocalMessage(txn, commentMsg, commentMeta,
					true, false);
			// Create the headers for the comment and the wrapped post
			oneOf(clientHelper).parseAndValidateAuthor(authorList2);
			will(returnValue(localAuthor2));
			oneOf(authorManager).getAuthorInfo(txn, localAuthor2.getId());
			will(returnValue(ourselvesInfo));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					wrappedPostId);
			will(returnValue(wrappedPostMeta));
			oneOf(clientHelper).parseAndValidateAuthor(authorList1);
			will(returnValue(localAuthor1));
			oneOf(authorManager).getAuthorInfo(txn, localAuthor1.getId());
			will(returnValue(verifiedInfo));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		BlogPostHeader originalPostHeader = new BlogPostHeader(POST,
				blog1.getId(), messageId, null, timestamp, timeReceived,
				localAuthor1, verifiedInfo, false, true);
		blogManager.addLocalComment(localAuthor2, blog2.getId(), comment,
				originalPostHeader);
		context.assertIsSatisfied();

		assertEquals(1, txn.getActions().size());
		CommitAction action = txn.getActions().get(0);
		assertTrue(action instanceof EventAction);
		Event event = ((EventAction) action).getEvent();
		assertTrue(event instanceof BlogPostAddedEvent);
		BlogPostAddedEvent e = (BlogPostAddedEvent) event;
		assertEquals(blog2.getId(), e.getGroupId());

		BlogPostHeader h = e.getHeader();
		assertEquals(COMMENT, h.getType());
		assertFalse(h.isRssFeed());
		assertEquals(timestamp, h.getTimestamp());
		assertEquals(timestamp, h.getTimeReceived());
		assertEquals(commentId, h.getId());
		assertEquals(blog2.getId(), h.getGroupId());
		assertEquals(wrappedPostId, h.getParentId());
		assertEquals(OURSELVES, h.getAuthorStatus());
		assertEquals(localAuthor2, h.getAuthor());

		assertTrue(h instanceof BlogCommentHeader);
		BlogPostHeader h1 = ((BlogCommentHeader) h).getParent();
		assertEquals(WRAPPED_POST, h1.getType());
		assertFalse(h1.isRssFeed());
		assertEquals(timestamp, h1.getTimestamp());
		assertEquals(timeReceived, h1.getTimeReceived());
		assertEquals(wrappedPostId, h1.getId());
		assertEquals(blog2.getId(), h1.getGroupId());
		assertNull(h1.getParentId());
		assertEquals(VERIFIED, h1.getAuthorStatus());
		assertEquals(localAuthor1, h1.getAuthor());

		assertEquals(h1.getId(), ((BlogCommentHeader) h).getRootPost().getId());
	}

	@Test
	public void testAddLocalCommentToRemoteRssPost() throws Exception {
		Transaction txn = new Transaction(null, false);
		// The post was originally posted to the RSS blog, then reblogged to
		// blog 1 with a comment
		BdfList originalPostBody = BdfList.of("originalPostBody");
		Message wrappedPostMsg = getMessage(blog1.getId());
		MessageId wrappedPostId = wrappedPostMsg.getId();
		BdfDictionary wrappedPostMeta = BdfDictionary.of(
				new BdfEntry(KEY_TYPE, WRAPPED_POST.getInt()),
				new BdfEntry(KEY_RSS_FEED, true),
				new BdfEntry(KEY_ORIGINAL_MSG_ID, rssMessageId),
				new BdfEntry(KEY_AUTHOR, rssAuthorList),
				new BdfEntry(KEY_TIMESTAMP, timestamp),
				new BdfEntry(KEY_TIME_RECEIVED, timeReceived)
		);
		Message commentMsg = getMessage(blog1.getId());
		MessageId commentId = commentMsg.getId();
		BdfDictionary commentMeta = BdfDictionary.of(
				new BdfEntry(KEY_TYPE, COMMENT.getInt()),
				new BdfEntry(KEY_COMMENT, comment),
				new BdfEntry(KEY_TIMESTAMP, timestamp),
				new BdfEntry(KEY_ORIGINAL_MSG_ID, commentId),
				new BdfEntry(KEY_ORIGINAL_PARENT_MSG_ID, rssMessageId),
				new BdfEntry(KEY_PARENT_MSG_ID, wrappedPostId),
				new BdfEntry(KEY_AUTHOR, authorList1)
		);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			// Wrap the original post for blog 1
			oneOf(clientHelper).getMessageAsList(txn, rssMessageId);
			will(returnValue(originalPostBody));
			oneOf(db).getGroup(txn, rssBlog.getId());
			will(returnValue(rssBlog.getGroup()));
			oneOf(blogPostFactory).wrapPost(blog1.getId(),
					rssBlog.getGroup().getDescriptor(), timestamp,
					originalPostBody);
			will(returnValue(wrappedPostMsg));
			oneOf(clientHelper).toList(rssLocalAuthor);
			will(returnValue(rssAuthorList));
			// Store the wrapped post
			oneOf(clientHelper).addLocalMessage(txn, wrappedPostMsg,
					wrappedPostMeta, true, false);
			// Create the comment
			oneOf(blogPostFactory).createBlogComment(blog1.getId(),
					localAuthor1, comment, rssMessageId, wrappedPostId);
			will(returnValue(commentMsg));
			oneOf(clientHelper).toList(localAuthor1);
			will(returnValue(authorList1));
			// Store the comment
			oneOf(clientHelper).addLocalMessage(txn, commentMsg, commentMeta,
					true, false);
			// Create the headers for the comment and the wrapped post
			oneOf(clientHelper).parseAndValidateAuthor(authorList1);
			will(returnValue(localAuthor1));
			oneOf(authorManager).getAuthorInfo(txn, localAuthor1.getId());
			will(returnValue(ourselvesInfo));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					wrappedPostId);
			will(returnValue(wrappedPostMeta));
			oneOf(clientHelper).parseAndValidateAuthor(rssAuthorList);
			will(returnValue(rssLocalAuthor));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		BlogPostHeader originalPostHeader = new BlogPostHeader(POST,
				rssBlog.getId(), rssMessageId, null, timestamp, timeReceived,
				rssLocalAuthor, new AuthorInfo(NONE), true, true);
		blogManager.addLocalComment(localAuthor1, blog1.getId(), comment,
				originalPostHeader);
		context.assertIsSatisfied();

		assertEquals(1, txn.getActions().size());
		CommitAction action = txn.getActions().get(0);
		assertTrue(action instanceof EventAction);
		Event event = ((EventAction) action).getEvent();
		assertTrue(event instanceof BlogPostAddedEvent);
		BlogPostAddedEvent e = (BlogPostAddedEvent) event;
		assertEquals(blog1.getId(), e.getGroupId());

		BlogPostHeader h = e.getHeader();
		assertEquals(COMMENT, h.getType());
		assertFalse(h.isRssFeed());
		assertEquals(timestamp, h.getTimestamp());
		assertEquals(timestamp, h.getTimeReceived());
		assertEquals(commentId, h.getId());
		assertEquals(blog1.getId(), h.getGroupId());
		assertEquals(wrappedPostId, h.getParentId());
		assertEquals(OURSELVES, h.getAuthorStatus());
		assertEquals(localAuthor1, h.getAuthor());

		assertTrue(h instanceof BlogCommentHeader);
		BlogPostHeader h1 = ((BlogCommentHeader) h).getParent();
		assertEquals(WRAPPED_POST, h1.getType());
		assertTrue(h1.isRssFeed());
		assertEquals(timestamp, h1.getTimestamp());
		assertEquals(timeReceived, h1.getTimeReceived());
		assertEquals(wrappedPostId, h1.getId());
		assertEquals(blog1.getId(), h1.getGroupId());
		assertNull(h1.getParentId());
		assertEquals(NONE, h1.getAuthorStatus());
		assertEquals(rssLocalAuthor, h1.getAuthor());

		assertEquals(h1.getId(), ((BlogCommentHeader) h).getRootPost().getId());
	}

	@Test
	public void testAddLocalCommentToRebloggedRemoteRssPost() throws Exception {
		Transaction txn = new Transaction(null, false);
		// The post was originally posted to the RSS blog, then reblogged to
		// blog 1 with a comment
		MessageId wrappedPostId = new MessageId(getRandomId());
		BdfList wrappedPostBody = BdfList.of("wrappedPostBody");
		MessageId originalCommentId = new MessageId(getRandomId());
		BdfList originalCommentBody = BdfList.of("originalCommentBody");
		// The post and comment were reblogged to blog 2 with another comment
		Message rewrappedPostMsg = getMessage(blog2.getId());
		MessageId rewrappedPostId = rewrappedPostMsg.getId();
		BdfDictionary rewrappedPostMeta = BdfDictionary.of(
				new BdfEntry(KEY_TYPE, WRAPPED_POST.getInt()),
				new BdfEntry(KEY_RSS_FEED, true),
				new BdfEntry(KEY_ORIGINAL_MSG_ID, messageId),
				new BdfEntry(KEY_AUTHOR, rssAuthorList),
				new BdfEntry(KEY_TIMESTAMP, timestamp),
				new BdfEntry(KEY_TIME_RECEIVED, timeReceived)
		);
		Message wrappedCommentMsg = getMessage(blog2.getId());
		MessageId wrappedCommentId = wrappedCommentMsg.getId();
		BdfDictionary wrappedCommentMeta = BdfDictionary.of(
				new BdfEntry(KEY_TYPE, WRAPPED_COMMENT.getInt()),
				new BdfEntry(KEY_COMMENT, comment),
				new BdfEntry(KEY_PARENT_MSG_ID, rewrappedPostId),
				new BdfEntry(KEY_ORIGINAL_MSG_ID, originalCommentId),
				new BdfEntry(KEY_AUTHOR, authorList1),
				new BdfEntry(KEY_TIMESTAMP, timestamp),
				new BdfEntry(KEY_TIME_RECEIVED, timeReceived)
		);
		String localComment = getRandomString(MAX_BLOG_COMMENT_TEXT_LENGTH);
		Message localCommentMsg = getMessage(blog2.getId());
		MessageId localCommentId = localCommentMsg.getId();
		BdfDictionary localCommentMeta = BdfDictionary.of(
				new BdfEntry(KEY_TYPE, COMMENT.getInt()),
				new BdfEntry(KEY_COMMENT, localComment),
				new BdfEntry(KEY_TIMESTAMP, timestamp),
				new BdfEntry(KEY_ORIGINAL_MSG_ID, localCommentId),
				new BdfEntry(KEY_ORIGINAL_PARENT_MSG_ID, originalCommentId),
				new BdfEntry(KEY_PARENT_MSG_ID, wrappedCommentId),
				new BdfEntry(KEY_AUTHOR, authorList2)
		);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			// Rewrap the wrapped post for blog 2
			oneOf(clientHelper).getMessageAsList(txn, wrappedPostId);
			will(returnValue(wrappedPostBody));
			oneOf(blogPostFactory).rewrapWrappedPost(blog2.getId(),
					wrappedPostBody);
			will(returnValue(rewrappedPostMsg));
			oneOf(clientHelper).toList(rssLocalAuthor);
			will(returnValue(rssAuthorList));
			// Store the rewrapped post
			oneOf(clientHelper).addLocalMessage(txn, rewrappedPostMsg,
					rewrappedPostMeta, true, false);
			// Wrap the original comment for blog 2
			oneOf(clientHelper).getMessageAsList(txn, originalCommentId);
			will(returnValue(originalCommentBody));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					wrappedPostId);
			will(returnValue(rewrappedPostMeta));
			oneOf(db).getGroup(txn, blog1.getId());
			will(returnValue(blog1.getGroup()));
			oneOf(blogPostFactory).wrapComment(blog2.getId(),
					blog1.getGroup().getDescriptor(), timestamp,
					originalCommentBody, rewrappedPostId);
			will(returnValue(wrappedCommentMsg));
			oneOf(clientHelper).toList(localAuthor1);
			will(returnValue(authorList1));
			// Store the wrapped comment
			oneOf(clientHelper).addLocalMessage(txn, wrappedCommentMsg,
					wrappedCommentMeta, true, false);
			// Create the new comment
			oneOf(blogPostFactory).createBlogComment(blog2.getId(),
					localAuthor2, localComment, originalCommentId,
					wrappedCommentId);
			will(returnValue(localCommentMsg));
			oneOf(clientHelper).toList(localAuthor2);
			will(returnValue(authorList2));
			// Store the new comment
			oneOf(clientHelper).addLocalMessage(txn, localCommentMsg,
					localCommentMeta, true, false);
			// Create the headers for the new comment, the wrapped comment and
			// the rewrapped post
			oneOf(clientHelper).parseAndValidateAuthor(authorList2);
			will(returnValue(localAuthor2));
			oneOf(authorManager).getAuthorInfo(txn, localAuthor2.getId());
			will(returnValue(ourselvesInfo));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					wrappedCommentId);
			will(returnValue(wrappedCommentMeta));
			oneOf(clientHelper).parseAndValidateAuthor(authorList1);
			will(returnValue(localAuthor1));
			oneOf(authorManager).getAuthorInfo(txn, localAuthor1.getId());
			will(returnValue(verifiedInfo));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					rewrappedPostId);
			will(returnValue(rewrappedPostMeta));
			oneOf(clientHelper).parseAndValidateAuthor(rssAuthorList);
			will(returnValue(rssLocalAuthor));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		BlogPostHeader wrappedPostHeader = new BlogPostHeader(WRAPPED_POST,
				blog1.getId(), wrappedPostId, null, timestamp, timeReceived,
				rssLocalAuthor, new AuthorInfo(NONE), true, true);
		BlogCommentHeader originalCommentHeader = new BlogCommentHeader(COMMENT,
				blog1.getId(), comment, wrappedPostHeader, originalCommentId,
				timestamp, timeReceived, localAuthor1, verifiedInfo, true);

		blogManager.addLocalComment(localAuthor2, blog2.getId(), localComment,
				originalCommentHeader);
		context.assertIsSatisfied();

		assertEquals(1, txn.getActions().size());
		CommitAction action = txn.getActions().get(0);
		assertTrue(action instanceof EventAction);
		Event event = ((EventAction) action).getEvent();
		assertTrue(event instanceof BlogPostAddedEvent);
		BlogPostAddedEvent e = (BlogPostAddedEvent) event;
		assertEquals(blog2.getId(), e.getGroupId());

		BlogPostHeader h = e.getHeader();
		assertEquals(COMMENT, h.getType());
		assertFalse(h.isRssFeed());
		assertEquals(timestamp, h.getTimestamp());
		assertEquals(timestamp, h.getTimeReceived());
		assertEquals(localCommentId, h.getId());
		assertEquals(blog2.getId(), h.getGroupId());
		assertEquals(wrappedCommentId, h.getParentId());
		assertEquals(OURSELVES, h.getAuthorStatus());
		assertEquals(localAuthor2, h.getAuthor());

		assertTrue(h instanceof BlogCommentHeader);
		BlogPostHeader h1 = ((BlogCommentHeader) h).getParent();
		assertEquals(WRAPPED_COMMENT, h1.getType());
		assertFalse(h1.isRssFeed());
		assertEquals(timestamp, h1.getTimestamp());
		assertEquals(timeReceived, h1.getTimeReceived());
		assertEquals(wrappedCommentId, h1.getId());
		assertEquals(blog2.getId(), h1.getGroupId());
		assertEquals(rewrappedPostId, h1.getParentId());
		assertEquals(VERIFIED, h1.getAuthorStatus());
		assertEquals(localAuthor1, h1.getAuthor());

		assertTrue(h1 instanceof BlogCommentHeader);
		BlogPostHeader h2 = ((BlogCommentHeader) h1).getParent();
		assertEquals(WRAPPED_POST, h2.getType());
		assertTrue(h2.isRssFeed());
		assertEquals(timestamp, h2.getTimestamp());
		assertEquals(timeReceived, h2.getTimeReceived());
		assertEquals(rewrappedPostId, h2.getId());
		assertEquals(blog2.getId(), h2.getGroupId());
		assertNull(h2.getParentId());
		assertEquals(NONE, h2.getAuthorStatus());
		assertEquals(rssLocalAuthor, h2.getAuthor());

		assertEquals(h2.getId(), ((BlogCommentHeader) h).getRootPost().getId());
		assertEquals(h2.getId(),
				((BlogCommentHeader) h1).getRootPost().getId());
	}

	@Test
	public void testBlogCanBeRemoved() throws Exception {
		// check that own personal blogs can not be removed
		Transaction txn = new Transaction(null, true);
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
		Transaction txn2 = new Transaction(null, true);
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

	private Blog createBlog(LocalAuthor localAuthor, boolean rssFeed) {
		Group group = getGroup(CLIENT_ID, MAJOR_VERSION);
		return new Blog(group, localAuthor, rssFeed);
	}

	private BdfList authorToBdfList(Author a) {
		return BdfList.of(a.getFormatVersion(), a.getName(), a.getPublicKey());
	}

}