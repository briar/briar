package org.briarproject.forum;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumPost;
import org.briarproject.api.forum.ForumPostHeader;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.briarproject.util.StringUtils;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.inject.Inject;

import static org.briarproject.api.forum.ForumConstants.FORUM_SALT_LENGTH;
import static org.briarproject.api.forum.ForumConstants.MAX_FORUM_NAME_LENGTH;
import static org.briarproject.api.identity.Author.Status.ANONYMOUS;
import static org.briarproject.api.identity.Author.Status.UNKNOWN;
import static org.briarproject.api.identity.Author.Status.VERIFIED;

class ForumManagerImpl implements ForumManager {

	static final ClientId CLIENT_ID = new ClientId(StringUtils.fromHexString(
			"859a7be50dca035b64bd6902fb797097"
					+ "795af837abbf8c16d750b3c2ccc186ea"));

	private final DatabaseComponent db;
	private final ClientHelper clientHelper;
	private final GroupFactory groupFactory;
	private final SecureRandom random;
	private final List<RemoveForumHook> removeHooks;

	@Inject
	ForumManagerImpl(DatabaseComponent db, ClientHelper clientHelper,
			GroupFactory groupFactory, SecureRandom random) {

		this.db = db;
		this.clientHelper = clientHelper;
		this.groupFactory = groupFactory;
		this.random = random;
		removeHooks = new CopyOnWriteArrayList<RemoveForumHook>();
	}

	@Override
	public ClientId getClientId() {
		return CLIENT_ID;
	}

	@Override
	public Forum createForum(String name) {
		int length = StringUtils.toUtf8(name).length;
		if (length == 0) throw new IllegalArgumentException();
		if (length > MAX_FORUM_NAME_LENGTH)
			throw new IllegalArgumentException();
		byte[] salt = new byte[FORUM_SALT_LENGTH];
		random.nextBytes(salt);
		return createForum(name, salt);
	}

	@Override
	public Forum createForum(String name, byte[] salt) {
		try {
			BdfList forum = BdfList.of(name, salt);
			byte[] descriptor = clientHelper.toByteArray(forum);
			Group g = groupFactory.createGroup(getClientId(), descriptor);
			return new Forum(g, name, salt);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void addForum(Forum f) throws DbException {
		Transaction txn = db.startTransaction(false);
		try {
			db.addGroup(txn, f.getGroup());
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public void removeForum(Forum f) throws DbException {
		Transaction txn = db.startTransaction(false);
		try {
			for (RemoveForumHook hook : removeHooks)
				hook.removingForum(txn, f);
			db.removeGroup(txn, f.getGroup());
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public void addLocalPost(ForumPost p) throws DbException {
		try {
			BdfDictionary meta = new BdfDictionary();
			meta.put("timestamp", p.getMessage().getTimestamp());
			if (p.getParent() != null) meta.put("parent", p.getParent());
			if (p.getAuthor() != null) {
				Author a = p.getAuthor();
				BdfDictionary authorMeta = new BdfDictionary();
				authorMeta.put("id", a.getId());
				authorMeta.put("name", a.getName());
				authorMeta.put("publicKey", a.getPublicKey());
				meta.put("author", authorMeta);
			}
			meta.put("contentType", p.getContentType());
			meta.put("local", true);
			meta.put("read", true);
			clientHelper.addLocalMessage(p.getMessage(), CLIENT_ID, meta, true);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Forum getForum(GroupId g) throws DbException {
		Forum forum;
		Transaction txn = db.startTransaction(true);
		try {
			forum = getForum(txn, g);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
		return forum;
	}

	@Override
	public Forum getForum(Transaction txn, GroupId g) throws DbException {
		try {
			Group group = db.getGroup(txn, g);
			return parseForum(group);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public Collection<Forum> getForums() throws DbException {
		try {
			Collection<Group> groups;
			Transaction txn = db.startTransaction(true);
			try {
				groups = db.getGroups(txn, CLIENT_ID);
				txn.setComplete();
			} finally {
				db.endTransaction(txn);
			}
			List<Forum> forums = new ArrayList<Forum>();
			for (Group g : groups) forums.add(parseForum(g));
			return Collections.unmodifiableList(forums);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public byte[] getPostBody(MessageId m) throws DbException {
		try {
			// Parent ID, author, content type, forum post body, signature
			BdfList message = clientHelper.getMessageAsList(m);
			return message.getRaw(3);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public Collection<ForumPostHeader> getPostHeaders(GroupId g)
			throws DbException {
		Set<AuthorId> localAuthorIds = new HashSet<AuthorId>();
		Set<AuthorId> contactAuthorIds = new HashSet<AuthorId>();
		Map<MessageId, BdfDictionary> metadata;
		Transaction txn = db.startTransaction(true);
		try {
			// Load the IDs of the user's identities
			for (LocalAuthor a : db.getLocalAuthors(txn))
				localAuthorIds.add(a.getId());
			// Load the IDs of contacts' identities
			for (Contact c : db.getContacts(txn))
				contactAuthorIds.add(c.getAuthor().getId());
			// Load the metadata
			metadata = clientHelper.getMessageMetadataAsDictionary(txn, g);
			txn.setComplete();
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
		// Parse the metadata
		Collection<ForumPostHeader> headers = new ArrayList<ForumPostHeader>();
		for (Entry<MessageId, BdfDictionary> entry : metadata.entrySet()) {
			try {
				BdfDictionary meta = entry.getValue();
				long timestamp = meta.getLong("timestamp");
				Author author = null;
				Author.Status authorStatus = ANONYMOUS;
				BdfDictionary d1 = meta.getDictionary("author", null);
				if (d1 != null) {
					AuthorId authorId = new AuthorId(d1.getRaw("id"));
					String name = d1.getString("name");
					byte[] publicKey = d1.getRaw("publicKey");
					author = new Author(authorId, name, publicKey);
					if (localAuthorIds.contains(authorId))
						authorStatus = VERIFIED;
					else if (contactAuthorIds.contains(authorId))
						authorStatus = VERIFIED;
					else authorStatus = UNKNOWN;
				}
				String contentType = meta.getString("contentType");
				boolean read = meta.getBoolean("read");
				headers.add(new ForumPostHeader(entry.getKey(), timestamp,
						author, authorStatus, contentType, read));
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
			meta.put("read", read);
			clientHelper.mergeMessageMetadata(m, meta);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void registerRemoveForumHook(RemoveForumHook hook) {
		removeHooks.add(hook);
	}

	private Forum parseForum(Group g) throws FormatException {
		byte[] descriptor = g.getDescriptor();
		// Name, salt
		BdfList forum = clientHelper.toList(descriptor, 0, descriptor.length);
		return new Forum(g, forum.getString(0), forum.getRaw(1));
	}

}
