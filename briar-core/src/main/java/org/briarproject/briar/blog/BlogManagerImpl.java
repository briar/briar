package org.briarproject.briar.blog;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.Author.Status;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Client;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.blog.Blog;
import org.briarproject.briar.api.blog.BlogCommentHeader;
import org.briarproject.briar.api.blog.BlogFactory;
import org.briarproject.briar.api.blog.BlogManager;
import org.briarproject.briar.api.blog.BlogPost;
import org.briarproject.briar.api.blog.BlogPostFactory;
import org.briarproject.briar.api.blog.BlogPostHeader;
import org.briarproject.briar.api.blog.MessageType;
import org.briarproject.briar.api.blog.event.BlogPostAddedEvent;
import org.briarproject.briar.client.BdfIncomingMessageHook;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static org.briarproject.bramble.api.contact.ContactManager.AddContactHook;
import static org.briarproject.bramble.api.contact.ContactManager.RemoveContactHook;
import static org.briarproject.bramble.api.sync.Group.Visibility.SHARED;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_AUTHOR;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_AUTHOR_ID;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_AUTHOR_NAME;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_COMMENT;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_ORIGINAL_MSG_ID;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_ORIGINAL_PARENT_MSG_ID;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_PARENT_MSG_ID;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_PUBLIC_KEY;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_READ;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_RSS_FEED;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_TIMESTAMP;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_TIME_RECEIVED;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_TYPE;
import static org.briarproject.briar.api.blog.MessageType.COMMENT;
import static org.briarproject.briar.api.blog.MessageType.POST;
import static org.briarproject.briar.api.blog.MessageType.WRAPPED_COMMENT;
import static org.briarproject.briar.api.blog.MessageType.WRAPPED_POST;
import static org.briarproject.briar.blog.BlogPostValidator.authorToBdfDictionary;

@NotNullByDefault
class BlogManagerImpl extends BdfIncomingMessageHook implements BlogManager,
		AddContactHook, RemoveContactHook, Client {

	private final IdentityManager identityManager;
	private final BlogFactory blogFactory;
	private final BlogPostFactory blogPostFactory;
	private final List<RemoveBlogHook> removeHooks;

	@Inject
	BlogManagerImpl(DatabaseComponent db, IdentityManager identityManager,
			ClientHelper clientHelper, MetadataParser metadataParser,
			BlogFactory blogFactory, BlogPostFactory blogPostFactory) {
		super(db, clientHelper, metadataParser);

		this.identityManager = identityManager;
		this.blogFactory = blogFactory;
		this.blogPostFactory = blogPostFactory;
		removeHooks = new CopyOnWriteArrayList<RemoveBlogHook>();
	}

	@Override
	public void createLocalState(Transaction txn) throws DbException {
		// Create our personal blog if necessary
		LocalAuthor a = identityManager.getLocalAuthor(txn);
		Blog b = blogFactory.createBlog(a);
		db.addGroup(txn, b.getGroup());
		// Ensure that we have the personal blogs of all contacts
		for (Contact c : db.getContacts(txn)) addingContact(txn, c);
	}

	@Override
	public void addingContact(Transaction txn, Contact c) throws DbException {
		// Add the personal blog of the contact and share it with the contact
		Blog b = blogFactory.createBlog(c.getAuthor());
		addBlog(txn, b);
		db.setGroupVisibility(txn, c.getId(), b.getId(), SHARED);
		// Share our personal blog with the contact
		LocalAuthor a = identityManager.getLocalAuthor(txn);
		Blog b2 = blogFactory.createBlog(a);
		db.setGroupVisibility(txn, c.getId(), b2.getId(), SHARED);
	}

	@Override
	public void removingContact(Transaction txn, Contact c) throws DbException {
		Blog b = blogFactory.createBlog(c.getAuthor());
		removeBlog(txn, b);
	}

	@Override
	protected boolean incomingMessage(Transaction txn, Message m, BdfList list,
			BdfDictionary meta) throws DbException, FormatException {

		GroupId groupId = m.getGroupId();
		MessageType type = getMessageType(meta);

		if (type == POST || type == COMMENT) {
			BlogPostHeader h =
					getPostHeaderFromMetadata(txn, groupId, m.getId(), meta);

			// check that original message IDs match
			if (type == COMMENT) {
				MessageId parentId = h.getParentId();
				if (parentId == null) throw new FormatException();
				BdfDictionary d = clientHelper
						.getMessageMetadataAsDictionary(txn, parentId);
				byte[] original1 = d.getRaw(KEY_ORIGINAL_MSG_ID);
				byte[] original2 = meta.getRaw(KEY_ORIGINAL_PARENT_MSG_ID);
				if (!Arrays.equals(original1, original2)) {
					throw new FormatException();
				}
			}

			// broadcast event about new post or comment
			BlogPostAddedEvent event =
					new BlogPostAddedEvent(groupId, h, false);
			txn.attach(event);

			// shares message and its dependencies
			return true;
		} else if (type == WRAPPED_COMMENT) {
			// Check that the original message ID in the dependency's metadata
			// matches the original parent ID of the wrapped comment
			MessageId dependencyId =
					new MessageId(meta.getRaw(KEY_PARENT_MSG_ID));
			BdfDictionary d = clientHelper
					.getMessageMetadataAsDictionary(txn, dependencyId);
			byte[] original1 = d.getRaw(KEY_ORIGINAL_MSG_ID);
			byte[] original2 = meta.getRaw(KEY_ORIGINAL_PARENT_MSG_ID);
			if (!Arrays.equals(original1, original2)) {
				throw new FormatException();
			}
		}
		// don't share message until parent arrives
		return false;
	}

	@Override
	public Blog addBlog(Author author) throws DbException {
		Blog b = blogFactory.createBlog(author);

		Transaction txn = db.startTransaction(false);
		try {
			db.addGroup(txn, b.getGroup());
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
		return b;
	}

	@Override
	public void addBlog(Transaction txn, Blog b) throws DbException {
		db.addGroup(txn, b.getGroup());
	}

	@Override
	public boolean canBeRemoved(Blog b) throws DbException {
		Transaction txn = db.startTransaction(true);
		try {
			boolean canBeRemoved = canBeRemoved(txn, b);
			db.commitTransaction(txn);
			return canBeRemoved;
		} finally {
			db.endTransaction(txn);
		}
	}

	private boolean canBeRemoved(Transaction txn, Blog b)
			throws DbException {
		AuthorId authorId = b.getAuthor().getId();
		LocalAuthor localAuthor = identityManager.getLocalAuthor(txn);
		return !localAuthor.getId().equals(authorId);
	}

	@Override
	public void removeBlog(Blog b) throws DbException {
		Transaction txn = db.startTransaction(false);
		try {
			removeBlog(txn, b);
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public void removeBlog(Transaction txn, Blog b) throws DbException {
		if (!canBeRemoved(txn, b))
			throw new IllegalArgumentException();
		for (RemoveBlogHook hook : removeHooks)
			hook.removingBlog(txn, b);
		db.removeGroup(txn, b.getGroup());
	}

	@Override
	public void addLocalPost(BlogPost p) throws DbException {
		Transaction txn = db.startTransaction(false);
		try {
			addLocalPost(txn, p);
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public void addLocalPost(Transaction txn, BlogPost p) throws DbException {
		try {
			GroupId groupId = p.getMessage().getGroupId();
			Blog b = getBlog(txn, groupId);

			BdfDictionary meta = new BdfDictionary();
			meta.put(KEY_TYPE, POST.getInt());
			meta.put(KEY_TIMESTAMP, p.getMessage().getTimestamp());
			meta.put(KEY_AUTHOR, authorToBdfDictionary(p.getAuthor()));
			meta.put(KEY_READ, true);
			meta.put(KEY_RSS_FEED, b.isRssFeed());
			clientHelper.addLocalMessage(txn, p.getMessage(), meta, true);

			// broadcast event about new post
			MessageId postId = p.getMessage().getId();
			BlogPostHeader h =
					getPostHeaderFromMetadata(txn, groupId, postId, meta);
			BlogPostAddedEvent event = new BlogPostAddedEvent(groupId, h, true);
			txn.attach(event);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public void addLocalComment(LocalAuthor author, GroupId groupId,
			@Nullable String comment, BlogPostHeader parentHeader)
			throws DbException {

		MessageType type = parentHeader.getType();
		if (type != POST && type != COMMENT)
			throw new IllegalArgumentException("Comment on unknown type!");

		Transaction txn = db.startTransaction(false);
		try {
			// Wrap post that we are commenting on
			MessageId parentOriginalId =
					getOriginalMessageId(txn, parentHeader);
			MessageId parentCurrentId =
					wrapMessage(txn, groupId, parentHeader, parentOriginalId);

			// Create actual comment
			Message message = blogPostFactory.createBlogComment(groupId, author,
					comment, parentOriginalId, parentCurrentId);
			BdfDictionary meta = new BdfDictionary();
			meta.put(KEY_TYPE, COMMENT.getInt());
			if (comment != null) meta.put(KEY_COMMENT, comment);
			meta.put(KEY_TIMESTAMP, message.getTimestamp());
			meta.put(KEY_ORIGINAL_MSG_ID, message.getId());
			meta.put(KEY_ORIGINAL_PARENT_MSG_ID, parentOriginalId);
			meta.put(KEY_PARENT_MSG_ID, parentCurrentId);
			meta.put(KEY_AUTHOR, authorToBdfDictionary(author));

			// Send comment
			clientHelper.addLocalMessage(txn, message, meta, true);

			// broadcast event
			BlogPostHeader h = getPostHeaderFromMetadata(txn, groupId,
					message.getId(), meta);
			BlogPostAddedEvent event = new BlogPostAddedEvent(groupId, h, true);
			txn.attach(event);
			db.commitTransaction(txn);
		} catch (FormatException e) {
			throw new DbException(e);
		} catch (GeneralSecurityException e) {
			throw new IllegalArgumentException("Invalid key of author", e);
		} finally {
			db.endTransaction(txn);
		}
	}

	private MessageId getOriginalMessageId(Transaction txn, BlogPostHeader h)
			throws DbException, FormatException {
		MessageType type = h.getType();
		if (type == POST || type == COMMENT) return h.getId();
		BdfDictionary meta = clientHelper.getMessageMetadataAsDictionary(txn,
				h.getId());
		return new MessageId(meta.getRaw(KEY_ORIGINAL_MSG_ID));
	}

	private MessageId wrapMessage(Transaction txn, GroupId groupId,
			BlogPostHeader header, MessageId originalId)
			throws DbException, FormatException {

		if (groupId.equals(header.getGroupId())) {
			// We are trying to wrap a post that is already in our group.
			// This is unnecessary, so just return the post's MessageId
			return header.getId();
		}

		// Get body of message to be wrapped
		BdfList body = clientHelper.getMessageAsList(txn, header.getId());
		if (body == null) throw new DbException();
		long timestamp = header.getTimestamp();
		Message wrappedMessage;

		BdfDictionary meta = new BdfDictionary();
		MessageType type = header.getType();
		if (type == POST) {
			// Wrap post
			Group group = db.getGroup(txn, header.getGroupId());
			byte[] descriptor = group.getDescriptor();
			wrappedMessage = blogPostFactory.wrapPost(groupId, descriptor,
					timestamp, body);
			meta.put(KEY_TYPE, WRAPPED_POST.getInt());
			meta.put(KEY_RSS_FEED, header.isRssFeed());
		} else if (type == COMMENT) {
			// Recursively wrap parent
			BlogCommentHeader commentHeader = (BlogCommentHeader) header;
			BlogPostHeader parentHeader = commentHeader.getParent();
			MessageId parentOriginalId =
					getOriginalMessageId(txn, parentHeader);
			MessageId parentCurrentId =
					wrapMessage(txn, groupId, parentHeader, parentOriginalId);
			// Wrap comment
			Group group = db.getGroup(txn, header.getGroupId());
			byte[] descriptor = group.getDescriptor();
			wrappedMessage = blogPostFactory.wrapComment(groupId, descriptor,
					timestamp, body, parentCurrentId);
			meta.put(KEY_TYPE, WRAPPED_COMMENT.getInt());
			if (commentHeader.getComment() != null)
				meta.put(KEY_COMMENT, commentHeader.getComment());
			meta.put(KEY_PARENT_MSG_ID, parentCurrentId);
		} else if (type == WRAPPED_POST) {
			// Re-wrap wrapped post without adding another wrapping layer
			wrappedMessage = blogPostFactory.rewrapWrappedPost(groupId, body);
			meta.put(KEY_TYPE, WRAPPED_POST.getInt());
			meta.put(KEY_RSS_FEED, header.isRssFeed());
		} else if (type == WRAPPED_COMMENT) {
			// Recursively wrap parent
			BlogCommentHeader commentHeader = (BlogCommentHeader) header;
			BlogPostHeader parentHeader = commentHeader.getParent();
			MessageId parentOriginalId =
					getOriginalMessageId(txn, parentHeader);
			MessageId parentCurrentId =
					wrapMessage(txn, groupId, parentHeader, parentOriginalId);
			// Re-wrap wrapped comment
			wrappedMessage = blogPostFactory.rewrapWrappedComment(groupId, body,
					parentCurrentId);
			meta.put(KEY_TYPE, WRAPPED_COMMENT.getInt());
			if (commentHeader.getComment() != null)
				meta.put(KEY_COMMENT, commentHeader.getComment());
			meta.put(KEY_PARENT_MSG_ID, parentCurrentId);
		} else {
			throw new IllegalArgumentException(
					"Unknown Message Type: " + type);
		}
		meta.put(KEY_ORIGINAL_MSG_ID, originalId);
		meta.put(KEY_AUTHOR, authorToBdfDictionary(header.getAuthor()));
		meta.put(KEY_TIMESTAMP, header.getTimestamp());
		meta.put(KEY_TIME_RECEIVED, header.getTimeReceived());

		// Send wrapped message and store metadata
		clientHelper.addLocalMessage(txn, wrappedMessage, meta, true);
		return wrappedMessage.getId();
	}

	@Override
	public Blog getBlog(GroupId g) throws DbException {
		Blog blog;
		Transaction txn = db.startTransaction(true);
		try {
			blog = getBlog(txn, g);
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
		return blog;
	}

	@Override
	public Blog getBlog(Transaction txn, GroupId g) throws DbException {
		try {
			Group group = db.getGroup(txn, g);
			return blogFactory.parseBlog(group);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public Collection<Blog> getBlogs(LocalAuthor localAuthor)
			throws DbException {

		Collection<Blog> allBlogs = getBlogs();
		List<Blog> blogs = new ArrayList<Blog>();
		for (Blog b : allBlogs) {
			if (b.getAuthor().equals(localAuthor)) {
				blogs.add(b);
			}
		}
		return blogs;
	}

	@Override
	public Blog getPersonalBlog(Author author) {
		return blogFactory.createBlog(author);
	}

	@Override
	public Collection<Blog> getBlogs() throws DbException {
		try {
			List<Blog> blogs = new ArrayList<Blog>();
			Collection<Group> groups;
			Transaction txn = db.startTransaction(true);
			try {
				groups = db.getGroups(txn, CLIENT_ID);
				for (Group g : groups) {
					blogs.add(blogFactory.parseBlog(g));
				}
				db.commitTransaction(txn);
			} finally {
				db.endTransaction(txn);
			}
			return blogs;
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public BlogPostHeader getPostHeader(GroupId g, MessageId m)
			throws DbException {
		Transaction txn = db.startTransaction(true);
		try {
			BdfDictionary meta =
					clientHelper.getMessageMetadataAsDictionary(txn, m);
			BlogPostHeader h = getPostHeaderFromMetadata(txn, g, m, meta);
			db.commitTransaction(txn);
			return h;
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public String getPostBody(MessageId m) throws DbException {
		try {
			BdfList message = clientHelper.getMessageAsList(m);
			if (message == null) throw new DbException();
			return getPostBody(message);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private String getPostBody(BdfList message) throws FormatException {
		MessageType type = MessageType.valueOf(message.getLong(0).intValue());
		if (type == POST) {
			// type, body, signature
			return message.getString(1);
		} else if (type == WRAPPED_POST) {
			// type, p_group descriptor, p_timestamp, p_content, p_signature
			return message.getString(3);
		} else {
			throw new FormatException();
		}
	}

	@Override
	public Collection<BlogPostHeader> getPostHeaders(GroupId g)
			throws DbException {

		// Query for posts and comments only
		BdfDictionary query1 = BdfDictionary.of(
				new BdfEntry(KEY_TYPE, POST.getInt())
		);
		BdfDictionary query2 = BdfDictionary.of(
				new BdfEntry(KEY_TYPE, COMMENT.getInt())
		);

		Collection<BlogPostHeader> headers = new ArrayList<BlogPostHeader>();
		Transaction txn = db.startTransaction(true);
		try {
			Map<MessageId, BdfDictionary> metadata1 =
					clientHelper.getMessageMetadataAsDictionary(txn, g, query1);
			Map<MessageId, BdfDictionary> metadata2 =
					clientHelper.getMessageMetadataAsDictionary(txn, g, query2);
			Map<MessageId, BdfDictionary> metadata =
					new HashMap<MessageId, BdfDictionary>(
							metadata1.size() + metadata2.size());
			metadata.putAll(metadata1);
			metadata.putAll(metadata2);
			// get all authors we need to get the status for
			Set<AuthorId> authors = new HashSet<AuthorId>();
			for (Entry<MessageId, BdfDictionary> entry : metadata.entrySet()) {
				authors.add(new AuthorId(
						entry.getValue().getDictionary(KEY_AUTHOR)
								.getRaw(KEY_AUTHOR_ID)));
			}
			// get statuses for all authors
			Map<AuthorId, Status> authorStatuses =
					new HashMap<AuthorId, Status>();
			for (AuthorId authorId : authors) {
				authorStatuses.put(authorId,
						identityManager.getAuthorStatus(txn, authorId));
			}
			// get post headers
			for (Entry<MessageId, BdfDictionary> entry : metadata.entrySet()) {
				BdfDictionary meta = entry.getValue();
				BlogPostHeader h =
						getPostHeaderFromMetadata(txn, g, entry.getKey(), meta,
								authorStatuses);
				headers.add(h);
			}
			db.commitTransaction(txn);
			return headers;
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public void setReadFlag(MessageId m, boolean read) throws DbException {
		try {
			BdfDictionary meta = new BdfDictionary();
			meta.put(KEY_READ, read);
			clientHelper.mergeMessageMetadata(m, meta);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void registerRemoveBlogHook(RemoveBlogHook hook) {
		removeHooks.add(hook);
	}

	private BlogPostHeader getPostHeaderFromMetadata(Transaction txn,
			GroupId groupId, MessageId id) throws DbException, FormatException {
		BdfDictionary meta =
				clientHelper.getMessageMetadataAsDictionary(txn, id);
		return getPostHeaderFromMetadata(txn, groupId, id, meta);
	}

	private BlogPostHeader getPostHeaderFromMetadata(Transaction txn,
			GroupId groupId, MessageId id, BdfDictionary meta)
			throws DbException, FormatException {
		return getPostHeaderFromMetadata(txn, groupId, id, meta,
				Collections.<AuthorId, Status>emptyMap());
	}

	private BlogPostHeader getPostHeaderFromMetadata(Transaction txn,
			GroupId groupId, MessageId id, BdfDictionary meta,
			Map<AuthorId, Status> authorStatuses)
			throws DbException, FormatException {

		MessageType type = getMessageType(meta);

		long timestamp = meta.getLong(KEY_TIMESTAMP);
		long timeReceived = meta.getLong(KEY_TIME_RECEIVED, timestamp);

		BdfDictionary d = meta.getDictionary(KEY_AUTHOR);
		AuthorId authorId = new AuthorId(d.getRaw(KEY_AUTHOR_ID));
		String name = d.getString(KEY_AUTHOR_NAME);
		byte[] publicKey = d.getRaw(KEY_PUBLIC_KEY);
		Author author = new Author(authorId, name, publicKey);
		boolean isFeedPost = meta.getBoolean(KEY_RSS_FEED, false);
		Status authorStatus;
		if (isFeedPost) {
			authorStatus = Status.NONE;
		} else if (authorStatuses.containsKey(authorId)) {
			authorStatus = authorStatuses.get(authorId);
		} else {
			authorStatus = identityManager.getAuthorStatus(txn, authorId);
		}

		boolean read = meta.getBoolean(KEY_READ, false);

		if (type == COMMENT || type == WRAPPED_COMMENT) {
			String comment = meta.getOptionalString(KEY_COMMENT);
			MessageId parentId = new MessageId(meta.getRaw(KEY_PARENT_MSG_ID));
			BlogPostHeader parent =
					getPostHeaderFromMetadata(txn, groupId, parentId);
			return new BlogCommentHeader(type, groupId, comment, parent, id,
					timestamp, timeReceived, author, authorStatus, read);
		} else {
			return new BlogPostHeader(type, groupId, id, timestamp,
					timeReceived, author, authorStatus, isFeedPost, read);
		}
	}

	private MessageType getMessageType(BdfDictionary d) throws FormatException {
		Long longType = d.getLong(KEY_TYPE);
		return MessageType.valueOf(longType.intValue());
	}
}
