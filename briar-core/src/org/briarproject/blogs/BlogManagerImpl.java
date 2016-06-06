package org.briarproject.blogs;

import org.briarproject.api.FormatException;
import org.briarproject.api.blogs.Blog;
import org.briarproject.api.blogs.BlogFactory;
import org.briarproject.api.blogs.BlogManager;
import org.briarproject.api.blogs.BlogPost;
import org.briarproject.api.blogs.BlogPostHeader;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfEntry;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.Author.Status;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.briarproject.util.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import javax.inject.Inject;

import static org.briarproject.api.blogs.BlogConstants.KEY_AUTHOR;
import static org.briarproject.api.blogs.BlogConstants.KEY_AUTHOR_ID;
import static org.briarproject.api.blogs.BlogConstants.KEY_AUTHOR_NAME;
import static org.briarproject.api.blogs.BlogConstants.KEY_CONTENT_TYPE;
import static org.briarproject.api.blogs.BlogConstants.KEY_DESCRIPTION;
import static org.briarproject.api.blogs.BlogConstants.KEY_HAS_BODY;
import static org.briarproject.api.blogs.BlogConstants.KEY_PARENT;
import static org.briarproject.api.blogs.BlogConstants.KEY_PUBLIC_KEY;
import static org.briarproject.api.blogs.BlogConstants.KEY_READ;
import static org.briarproject.api.blogs.BlogConstants.KEY_TEASER;
import static org.briarproject.api.blogs.BlogConstants.KEY_TIMESTAMP;
import static org.briarproject.api.blogs.BlogConstants.KEY_TITLE;

class BlogManagerImpl implements BlogManager {

	private static final Logger LOG =
			Logger.getLogger(BlogManagerImpl.class.getName());

	static final ClientId CLIENT_ID = new ClientId(StringUtils.fromHexString(
			"dafbe56f0c8971365cea4bb5f08ec9a6" +
					"1d686e058b943997b6ff259ba423f613"));

	private final DatabaseComponent db;
	private final IdentityManager identityManager;
	private final ClientHelper clientHelper;
	private final BlogFactory blogFactory;

	@Inject
	BlogManagerImpl(DatabaseComponent db, IdentityManager identityManager,
			ClientHelper clientHelper, BlogFactory blogFactory) {

		this.db = db;
		this.identityManager = identityManager;
		this.clientHelper = clientHelper;
		this.blogFactory = blogFactory;
	}

	@Override
	public ClientId getClientId() {
		return CLIENT_ID;
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
	public void addLocalPost(BlogPost p) throws DbException {
		try {
			BdfDictionary meta = new BdfDictionary();
			if (p.getTitle() != null) meta.put(KEY_TITLE, p.getTitle());
			meta.put(KEY_TEASER, p.getTeaser());
			meta.put(KEY_TIMESTAMP, p.getMessage().getTimestamp());
			meta.put(KEY_HAS_BODY, p.hasBody());
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
			// content, signature
			// content: parent, contentType, title, teaser, body, attachments
			BdfList message = clientHelper.getMessageAsList(m);
			BdfList content = message.getList(0);
			return content.getRaw(4);
		} catch (FormatException e) {
			throw new DbException(e);
		}
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
				String title = meta.getOptionalString(KEY_TITLE);
				String teaser = meta.getString(KEY_TEASER);
				boolean hasBody = meta.getBoolean(KEY_HAS_BODY);
				long timestamp = meta.getLong(KEY_TIMESTAMP);
				MessageId parentId = null;
				if (meta.containsKey(KEY_PARENT))
					parentId = new MessageId(meta.getRaw(KEY_PARENT));

				BdfDictionary d = meta.getDictionary(KEY_AUTHOR);
				AuthorId authorId = new AuthorId(d.getRaw(KEY_AUTHOR_ID));
				String name = d.getString(KEY_AUTHOR_NAME);
				byte[] publicKey = d.getRaw(KEY_PUBLIC_KEY);
				Author author = new Author(authorId, name, publicKey);
				Status authorStatus = identityManager.getAuthorStatus(authorId);

				String contentType = meta.getString(KEY_CONTENT_TYPE);
				boolean read = meta.getBoolean(KEY_READ);
				headers.add(new BlogPostHeader(title, teaser, hasBody,
						entry.getKey(), parentId, timestamp, author,
						authorStatus, contentType, read));
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

	private String getBlogDescription(Transaction txn, GroupId g)
			throws DbException, FormatException {
		BdfDictionary d = clientHelper.getGroupMetadataAsDictionary(txn, g);
		return d.getString(KEY_DESCRIPTION);
	}

}
