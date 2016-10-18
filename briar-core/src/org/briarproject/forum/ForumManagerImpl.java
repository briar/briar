package org.briarproject.forum;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataParser;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.event.ForumPostReceivedEvent;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumFactory;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumPost;
import org.briarproject.api.forum.ForumPostFactory;
import org.briarproject.api.forum.ForumPostHeader;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.Author.Status;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.system.Clock;
import org.briarproject.clients.BdfIncomingMessageHook;
import org.briarproject.util.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.inject.Inject;

import static org.briarproject.api.forum.ForumConstants.KEY_AUTHOR;
import static org.briarproject.api.forum.ForumConstants.KEY_ID;
import static org.briarproject.api.forum.ForumConstants.KEY_LOCAL;
import static org.briarproject.api.forum.ForumConstants.KEY_NAME;
import static org.briarproject.api.forum.ForumConstants.KEY_PARENT;
import static org.briarproject.api.forum.ForumConstants.KEY_PUBLIC_NAME;
import static org.briarproject.api.forum.ForumConstants.KEY_TIMESTAMP;
import static org.briarproject.api.identity.Author.Status.ANONYMOUS;
import static org.briarproject.api.identity.Author.Status.OURSELVES;
import static org.briarproject.clients.BdfConstants.MSG_KEY_READ;

class ForumManagerImpl extends BdfIncomingMessageHook implements ForumManager {

	static final ClientId CLIENT_ID = new ClientId(StringUtils.fromHexString(
			"859a7be50dca035b64bd6902fb797097"
					+ "795af837abbf8c16d750b3c2ccc186ea"));

	private final IdentityManager identityManager;
	private final ForumFactory forumFactory;
	private final ForumPostFactory forumPostFactory;
	private final Clock clock;
	private final List<RemoveForumHook> removeHooks;

	@Inject
	ForumManagerImpl(DatabaseComponent db, IdentityManager identityManager,
			ClientHelper clientHelper, MetadataParser metadataParser,
			ForumFactory forumFactory, ForumPostFactory forumPostFactory,
			Clock clock) {
		super(db, clientHelper, metadataParser);

		this.identityManager = identityManager;
		this.forumFactory = forumFactory;
		this.forumPostFactory = forumPostFactory;
		this.clock = clock;
		removeHooks = new CopyOnWriteArrayList<RemoveForumHook>();
	}

	@Override
	protected boolean incomingMessage(Transaction txn, Message m, BdfList body,
			BdfDictionary meta) throws DbException, FormatException {

		trackIncomingMessage(txn, m);

		ForumPostHeader post = getForumPostHeader(txn, m.getId(), meta);
		ForumPostReceivedEvent event =
				new ForumPostReceivedEvent(post, m.getGroupId());
		txn.attach(event);

		// share message
		return true;
	}

	@Override
	public ClientId getClientId() {
		return CLIENT_ID;
	}

	@Override
	public Forum addForum(String name) throws DbException {
		Forum f = forumFactory.createForum(name);

		Transaction txn = db.startTransaction(false);
		try {
			db.addGroup(txn, f.getGroup());
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
		return f;
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
	public ForumPost createLocalPost(final GroupId groupId,
			final String body, final @Nullable MessageId parentId)
			throws DbException {

		LocalAuthor author;
		GroupCount count;
		Transaction txn = db.startTransaction(true);
		try {
			author = identityManager.getLocalAuthor(txn);
			count = getGroupCount(txn, groupId);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
		long timestamp = clock.currentTimeMillis();
		timestamp = Math.max(timestamp, count.getLatestMsgTime());

		ForumPost p;
		try {
			p = forumPostFactory
					.createPseudonymousPost(groupId, timestamp, parentId,
							author, body);
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
		return p;
	}

	@Override
	public ForumPostHeader addLocalPost(ForumPost p) throws DbException {
		Transaction txn = db.startTransaction(false);
		try {
			BdfDictionary meta = new BdfDictionary();
			meta.put(KEY_TIMESTAMP, p.getMessage().getTimestamp());
			if (p.getParent() != null) meta.put(KEY_PARENT, p.getParent());
			if (p.getAuthor() != null) {
				Author a = p.getAuthor();
				BdfDictionary authorMeta = new BdfDictionary();
				authorMeta.put(KEY_ID, a.getId());
				authorMeta.put(KEY_NAME, a.getName());
				authorMeta.put(KEY_PUBLIC_NAME, a.getPublicKey());
				meta.put(KEY_AUTHOR, authorMeta);
			}
			meta.put(KEY_LOCAL, true);
			meta.put(MSG_KEY_READ, true);
			clientHelper.addLocalMessage(txn, p.getMessage(), meta, true);
			trackOutgoingMessage(txn, p.getMessage());
			txn.setComplete();
		} catch (FormatException e) {
			throw new RuntimeException(e);
		} finally {
			db.endTransaction(txn);
		}
		return new ForumPostHeader(p.getMessage().getId(), p.getParent(),
				p.getMessage().getTimestamp(), p.getAuthor(), OURSELVES, true);
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

		Collection<ForumPostHeader> headers = new ArrayList<ForumPostHeader>();
		Transaction txn = db.startTransaction(true);
		try {
			Map<MessageId, BdfDictionary> metadata =
					clientHelper.getMessageMetadataAsDictionary(txn, g);
			// get all authors we need to get the status for
			Set<AuthorId> authors = new HashSet<AuthorId>();
			for (Entry<MessageId, BdfDictionary> entry : metadata.entrySet()) {
				BdfDictionary d =
						entry.getValue().getDictionary(KEY_AUTHOR, null);
				if (d != null)
					authors.add(new AuthorId(d.getRaw(KEY_ID)));
			}
			// get statuses for all authors
			Map<AuthorId, Status> statuses = new HashMap<AuthorId, Status>();
			for (AuthorId id : authors) {
				statuses.put(id, identityManager.getAuthorStatus(txn, id));
			}
			// Parse the metadata
			for (Entry<MessageId, BdfDictionary> entry : metadata
					.entrySet()) {
				BdfDictionary meta = entry.getValue();
				headers.add(getForumPostHeader(txn, entry.getKey(), meta,
						statuses));
			}
			txn.setComplete();
			return headers;
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public void registerRemoveForumHook(RemoveForumHook hook) {
		removeHooks.add(hook);
	}

	private Forum parseForum(Group g) throws FormatException {
		byte[] descriptor = g.getDescriptor();
		// Name, salt
		BdfList forum = clientHelper.toList(descriptor);
		return new Forum(g, forum.getString(0), forum.getRaw(1));
	}

	private ForumPostHeader getForumPostHeader(Transaction txn, MessageId id,
			BdfDictionary meta) throws DbException, FormatException {
		return getForumPostHeader(txn, id, meta,
				Collections.<AuthorId, Status>emptyMap());
	}

	private ForumPostHeader getForumPostHeader(Transaction txn, MessageId id,
			BdfDictionary meta, Map<AuthorId, Status> statuses)
			throws DbException, FormatException {

		long timestamp = meta.getLong(KEY_TIMESTAMP);
		Author author = null;
		Status status = ANONYMOUS;
		MessageId parentId = null;
		if (meta.containsKey(KEY_PARENT))
			parentId = new MessageId(meta.getRaw(KEY_PARENT));
		BdfDictionary d1 = meta.getDictionary(KEY_AUTHOR, null);
		if (d1 != null) {
			AuthorId authorId = new AuthorId(d1.getRaw(KEY_ID));
			String name = d1.getString(KEY_NAME);
			byte[] publicKey = d1.getRaw(KEY_PUBLIC_NAME);
			author = new Author(authorId, name, publicKey);
			if (statuses.containsKey(authorId)) {
				status = statuses.get(authorId);
			} else {
				status = identityManager.getAuthorStatus(txn, author.getId());
			}
		}
		boolean read = meta.getBoolean(MSG_KEY_READ);

		return new ForumPostHeader(id, parentId, timestamp, author, status,
				read);
	}

}
