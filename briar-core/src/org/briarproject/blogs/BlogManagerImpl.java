package org.briarproject.blogs;

import org.briarproject.api.FormatException;
import org.briarproject.api.blogs.Blog;
import org.briarproject.api.blogs.BlogFactory;
import org.briarproject.api.blogs.BlogManager;
import org.briarproject.api.blogs.BlogPost;
import org.briarproject.api.blogs.BlogPostHeader;
import org.briarproject.api.clients.Client;
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
import org.briarproject.api.identity.Author.Status;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.IdentityManager.AddIdentityHook;
import org.briarproject.api.identity.IdentityManager.RemoveIdentityHook;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.clients.BdfIncomingMessageHook;
import org.briarproject.util.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static org.briarproject.api.blogs.BlogConstants.KEY_AUTHOR;
import static org.briarproject.api.blogs.BlogConstants.KEY_AUTHOR_ID;
import static org.briarproject.api.blogs.BlogConstants.KEY_AUTHOR_NAME;
import static org.briarproject.api.blogs.BlogConstants.KEY_CONTENT_TYPE;
import static org.briarproject.api.blogs.BlogConstants.KEY_DESCRIPTION;
import static org.briarproject.api.blogs.BlogConstants.KEY_PARENT;
import static org.briarproject.api.blogs.BlogConstants.KEY_PUBLIC_KEY;
import static org.briarproject.api.blogs.BlogConstants.KEY_READ;
import static org.briarproject.api.blogs.BlogConstants.KEY_TIMESTAMP;
import static org.briarproject.api.blogs.BlogConstants.KEY_TIME_RECEIVED;
import static org.briarproject.api.blogs.BlogConstants.KEY_TITLE;
import static org.briarproject.api.contact.ContactManager.AddContactHook;
import static org.briarproject.api.contact.ContactManager.RemoveContactHook;

class BlogManagerImpl extends BdfIncomingMessageHook implements BlogManager,
		AddContactHook, RemoveContactHook, Client,
		AddIdentityHook, RemoveIdentityHook {

	private static final Logger LOG =
			Logger.getLogger(BlogManagerImpl.class.getName());

	static final ClientId CLIENT_ID = new ClientId(StringUtils.fromHexString(
			"dafbe56f0c8971365cea4bb5f08ec9a6" +
					"1d686e058b943997b6ff259ba423f613"));

	private final DatabaseComponent db;
	private final IdentityManager identityManager;
	private final BlogFactory blogFactory;
	private final List<RemoveBlogHook> removeHooks;

	@Inject
	BlogManagerImpl(DatabaseComponent db, IdentityManager identityManager,
			ClientHelper clientHelper, MetadataParser metadataParser,
			BlogFactory blogFactory) {
		super(clientHelper, metadataParser);

		this.db = db;
		this.identityManager = identityManager;
		this.blogFactory = blogFactory;
		removeHooks = new CopyOnWriteArrayList<RemoveBlogHook>();
	}

	@Override
	public ClientId getClientId() {
		return CLIENT_ID;
	}

	@Override
	public void createLocalState(Transaction txn) throws DbException {
		// Ensure every identity does have its own personal blog
		// TODO this can probably be removed once #446 is resolved and all users migrated to a new version
		for (LocalAuthor a : db.getLocalAuthors(txn)) {
			Blog b = blogFactory.createPersonalBlog(a);
			Group g = b.getGroup();
			if (!db.containsGroup(txn, g.getId())) {
				db.addGroup(txn, g);
				for (ContactId c : db.getContacts(txn, a.getId())) {
					db.setVisibleToContact(txn, c, g.getId(), true);
				}
			}
		}
		// Ensure that we have the personal blogs of all pre-existing contacts
		for (Contact c : db.getContacts(txn)) addingContact(txn, c);
	}

	@Override
	public void addingContact(Transaction txn, Contact c) throws DbException {
		// get personal blog of the contact
		Blog b = blogFactory.createPersonalBlog(c.getAuthor());
		Group g = b.getGroup();
		if (!db.containsGroup(txn, g.getId())) {
			// add the personal blog of the contact
			db.addGroup(txn, g);
			db.setVisibleToContact(txn, c.getId(), g.getId(), true);

			// share our personal blog with the new contact
			LocalAuthor a = db.getLocalAuthor(txn, c.getLocalAuthorId());
			Blog b2 = blogFactory.createPersonalBlog(a);
			db.setVisibleToContact(txn, c.getId(), b2.getId(), true);
		}
	}

	@Override
	public void removingContact(Transaction txn, Contact c) throws DbException {
		if (c != null) {
			Blog b = blogFactory.createPersonalBlog(c.getAuthor());
			db.removeGroup(txn, b.getGroup());
		}
	}

	@Override
	public void addingIdentity(Transaction txn, LocalAuthor a)
			throws DbException {

		// add a personal blog for the new identity
		LOG.info("New Personal Blog Added.");
		Blog b = blogFactory.createPersonalBlog(a);
		db.addGroup(txn, b.getGroup());
	}

	@Override
	public void removingIdentity(Transaction txn, LocalAuthor a)
			throws DbException {

		// remove the personal blog of that identity
		Blog b = blogFactory.createPersonalBlog(a);
		db.removeGroup(txn, b.getGroup());
	}

	@Override
	protected void incomingMessage(Transaction txn, Message m, BdfList list,
			BdfDictionary meta) throws DbException, FormatException {

		GroupId groupId = m.getGroupId();
		BlogPostHeader h = getPostHeaderFromMetadata(txn, m.getId(), meta);
		BlogPostAddedEvent event =
				new BlogPostAddedEvent(groupId, h, false);
		txn.attach(event);
	}

	@Override
	public Blog addBlog(LocalAuthor localAuthor, String name,
			String description) throws DbException {

		Blog b = blogFactory
				.createBlog(name, description, localAuthor);
		BdfDictionary metadata = BdfDictionary.of(
				new BdfEntry(KEY_DESCRIPTION, b.getDescription())
		);

		Transaction txn = db.startTransaction(false);
		try {
			db.addGroup(txn, b.getGroup());
			clientHelper.mergeGroupMetadata(txn, b.getId(), metadata);
			txn.setComplete();
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
		return b;
	}

	@Override
	public void removeBlog(Blog b) throws DbException {
		// TODO if this gets used, check for RSS feeds posting into this blog
		Transaction txn = db.startTransaction(false);
		try {
			for (RemoveBlogHook hook : removeHooks)
				hook.removingBlog(txn, b);
			db.removeGroup(txn, b.getGroup());
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public void addLocalPost(BlogPost p) throws DbException {
		BdfDictionary meta;
		try {
			meta = new BdfDictionary();
			if (p.getTitle() != null) meta.put(KEY_TITLE, p.getTitle());
			meta.put(KEY_TIMESTAMP, p.getMessage().getTimestamp());
			if (p.getParent() != null) meta.put(KEY_PARENT, p.getParent());

			Author a = p.getAuthor();
			BdfDictionary authorMeta = new BdfDictionary();
			authorMeta.put(KEY_AUTHOR_ID, a.getId());
			authorMeta.put(KEY_AUTHOR_NAME, a.getName());
			authorMeta.put(KEY_PUBLIC_KEY, a.getPublicKey());
			meta.put(KEY_AUTHOR, authorMeta);

			meta.put(KEY_CONTENT_TYPE, p.getContentType());
			meta.put(KEY_READ, true);
			clientHelper.addLocalMessage(p.getMessage(), CLIENT_ID, meta, true);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}

		// broadcast event about new post
		Transaction txn = db.startTransaction(true);
		try {
			GroupId groupId = p.getMessage().getGroupId();
			MessageId postId = p.getMessage().getId();
			BlogPostHeader h = getPostHeaderFromMetadata(txn, postId, meta);
			BlogPostAddedEvent event =
					new BlogPostAddedEvent(groupId, h, true);
			txn.attach(event);
			txn.setComplete();
		} catch (FormatException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public Blog getBlog(GroupId g) throws DbException {
		Blog blog;
		Transaction txn = db.startTransaction(true);
		try {
			blog = getBlog(txn, g);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
		return blog;
	}

	@Override
	public Blog getBlog(Transaction txn, GroupId g) throws DbException {
		try {
			Group group = db.getGroup(txn, g);
			String description = getBlogDescription(txn, g);
			return blogFactory.parseBlog(group, description);
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
		return Collections.unmodifiableList(blogs);
	}

	@Override
	public Blog getPersonalBlog(Author author) throws DbException {
		return blogFactory.createPersonalBlog(author);
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
					String description = getBlogDescription(txn, g.getId());
					blogs.add(blogFactory.parseBlog(g, description));
				}
				txn.setComplete();
			} finally {
				db.endTransaction(txn);
			}
			return Collections.unmodifiableList(blogs);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	@Nullable
	public byte[] getPostBody(MessageId m) throws DbException {
		try {
			BdfList message = clientHelper.getMessageAsList(m);
			return getPostBody(message);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private byte[] getPostBody(BdfList message) throws FormatException {
		// content, signature
		// content: parent, contentType, title, body, attachments
		BdfList content = message.getList(0);
		return content.getRaw(3);
	}

	@Override
	public Collection<BlogPostHeader> getPostHeaders(GroupId g)
			throws DbException {

		Map<MessageId, BdfDictionary> metadata;
		try {
			metadata = clientHelper.getMessageMetadataAsDictionary(g);
		} catch (FormatException e) {
			throw new DbException(e);
		}
		Collection<BlogPostHeader> headers = new ArrayList<BlogPostHeader>();
		for (Entry<MessageId, BdfDictionary> entry : metadata.entrySet()) {
			try {
				BdfDictionary meta = entry.getValue();
				BlogPostHeader h =
						getPostHeaderFromMetadata(null, entry.getKey(), meta);
				headers.add(h);
			} catch (FormatException e) {
				throw new DbException(e);
			}
		}
		return headers;
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

	private String getBlogDescription(Transaction txn, GroupId g)
			throws DbException, FormatException {
		BdfDictionary d = clientHelper.getGroupMetadataAsDictionary(txn, g);
		return d.getString(KEY_DESCRIPTION, "");
	}

	private BlogPostHeader getPostHeaderFromMetadata(@Nullable Transaction txn,
			MessageId id, BdfDictionary meta)
			throws DbException, FormatException {

		String title = meta.getOptionalString(KEY_TITLE);
		long timestamp = meta.getLong(KEY_TIMESTAMP);
		long timeReceived = meta.getLong(KEY_TIME_RECEIVED, timestamp);
		MessageId parentId = null;
		if (meta.containsKey(KEY_PARENT))
			parentId = new MessageId(meta.getRaw(KEY_PARENT));

		BdfDictionary d = meta.getDictionary(KEY_AUTHOR);
		AuthorId authorId = new AuthorId(d.getRaw(KEY_AUTHOR_ID));
		String name = d.getString(KEY_AUTHOR_NAME);
		byte[] publicKey = d.getRaw(KEY_PUBLIC_KEY);
		Author author = new Author(authorId, name, publicKey);
		Status authorStatus;
		if (txn == null)
			authorStatus = identityManager.getAuthorStatus(authorId);
		else {
			authorStatus = identityManager.getAuthorStatus(txn, authorId);
		}

		String contentType = meta.getString(KEY_CONTENT_TYPE);
		boolean read = meta.getBoolean(KEY_READ);
		return new BlogPostHeader(title, id, parentId, timestamp, timeReceived,
				author, authorStatus, contentType, read);
	}
}
