package org.briarproject.briar.forum;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfDictionary;
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
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.client.MessageTracker.GroupCount;
import org.briarproject.briar.api.forum.Forum;
import org.briarproject.briar.api.forum.ForumFactory;
import org.briarproject.briar.api.forum.ForumManager;
import org.briarproject.briar.api.forum.ForumPost;
import org.briarproject.briar.api.forum.ForumPostFactory;
import org.briarproject.briar.api.forum.ForumPostHeader;
import org.briarproject.briar.api.forum.event.ForumPostReceivedEvent;
import org.briarproject.briar.client.BdfIncomingMessageHook;

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

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import static org.briarproject.bramble.api.identity.Author.Status.OURSELVES;
import static org.briarproject.briar.api.forum.ForumConstants.KEY_AUTHOR;
import static org.briarproject.briar.api.forum.ForumConstants.KEY_LOCAL;
import static org.briarproject.briar.api.forum.ForumConstants.KEY_PARENT;
import static org.briarproject.briar.api.forum.ForumConstants.KEY_TIMESTAMP;
import static org.briarproject.briar.client.MessageTrackerConstants.MSG_KEY_READ;

@ThreadSafe
@NotNullByDefault
class ForumManagerImpl extends BdfIncomingMessageHook implements ForumManager {

	private final IdentityManager identityManager;
	private final ForumFactory forumFactory;
	private final ForumPostFactory forumPostFactory;
	private final MessageTracker messageTracker;
	private final List<RemoveForumHook> removeHooks;

	@Inject
	ForumManagerImpl(DatabaseComponent db, IdentityManager identityManager,
			ClientHelper clientHelper, MetadataParser metadataParser,
			ForumFactory forumFactory, ForumPostFactory forumPostFactory,
			MessageTracker messageTracker) {
		super(db, clientHelper, metadataParser);
		this.identityManager = identityManager;
		this.forumFactory = forumFactory;
		this.forumPostFactory = forumPostFactory;
		this.messageTracker = messageTracker;
		removeHooks = new CopyOnWriteArrayList<>();
	}

	@Override
	protected boolean incomingMessage(Transaction txn, Message m, BdfList body,
			BdfDictionary meta) throws DbException, FormatException {

		messageTracker.trackIncomingMessage(txn, m);

		ForumPostHeader header = getForumPostHeader(txn, m.getId(), meta);
		String text = getPostText(body);
		ForumPostReceivedEvent event =
				new ForumPostReceivedEvent(m.getGroupId(), header, text);
		txn.attach(event);

		// share message
		return true;
	}

	@Override
	public Forum addForum(String name) throws DbException {
		Forum f = forumFactory.createForum(name);
		db.transaction(false, txn -> db.addGroup(txn, f.getGroup()));
		return f;
	}

	@Override
	public void addForum(Transaction txn, Forum f) throws DbException {
		db.addGroup(txn, f.getGroup());
	}

	@Override
	public void removeForum(Forum f) throws DbException {
		db.transaction(false, txn -> {
			for (RemoveForumHook hook : removeHooks)
				hook.removingForum(txn, f);
			db.removeGroup(txn, f.getGroup());
		});
	}

	@Override
	public ForumPost createLocalPost(GroupId groupId, String text,
			long timestamp, @Nullable MessageId parentId, LocalAuthor author) {
		ForumPost p;
		try {
			p = forumPostFactory.createPost(groupId, timestamp, parentId,
					author, text);
		} catch (GeneralSecurityException | FormatException e) {
			throw new AssertionError(e);
		}
		return p;
	}

	@Override
	public ForumPostHeader addLocalPost(ForumPost p) throws DbException {
		db.transaction(false, txn -> {
			try {
				BdfDictionary meta = new BdfDictionary();
				meta.put(KEY_TIMESTAMP, p.getMessage().getTimestamp());
				if (p.getParent() != null) meta.put(KEY_PARENT, p.getParent());
				Author a = p.getAuthor();
				meta.put(KEY_AUTHOR, clientHelper.toList(a));
				meta.put(KEY_LOCAL, true);
				meta.put(MSG_KEY_READ, true);
				clientHelper.addLocalMessage(txn, p.getMessage(), meta, true);
				messageTracker.trackOutgoingMessage(txn, p.getMessage());
			} catch (FormatException e) {
				throw new AssertionError(e);
			}
		});
		return new ForumPostHeader(p.getMessage().getId(), p.getParent(),
				p.getMessage().getTimestamp(), p.getAuthor(), OURSELVES, true);
	}

	@Override
	public Forum getForum(GroupId g) throws DbException {
		return db.transactionWithResult(true, txn -> getForum(txn, g));
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
		Collection<Group> groups = db.transactionWithResult(true, txn ->
				db.getGroups(txn, CLIENT_ID, MAJOR_VERSION));
		try {
			List<Forum> forums = new ArrayList<>();
			for (Group g : groups) forums.add(parseForum(g));
			return forums;
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public String getPostText(MessageId m) throws DbException {
		try {
			return getPostText(clientHelper.getMessageAsList(m));
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private String getPostText(BdfList body) throws FormatException {
		// Parent ID, author, text, signature
		return body.getString(2);
	}

	@Override
	public Collection<ForumPostHeader> getPostHeaders(GroupId g)
			throws DbException {
		try {
			return db.transactionWithResult(true, txn -> {
				Collection<ForumPostHeader> headers = new ArrayList<>();
				Map<MessageId, BdfDictionary> metadata =
						clientHelper.getMessageMetadataAsDictionary(txn, g);
				// get all authors we need to get the status for
				Set<AuthorId> authors = new HashSet<>();
				for (Entry<MessageId, BdfDictionary> entry :
						metadata.entrySet()) {
					BdfList authorList = entry.getValue().getList(KEY_AUTHOR);
					Author a = clientHelper.parseAndValidateAuthor(authorList);
					authors.add(a.getId());
				}
				// get statuses for all authors
				Map<AuthorId, Status> statuses = new HashMap<>();
				for (AuthorId id : authors) {
					statuses.put(id, identityManager.getAuthorStatus(txn, id));
				}
				// Parse the metadata
				for (Entry<MessageId, BdfDictionary> entry :
						metadata.entrySet()) {
					BdfDictionary meta = entry.getValue();
					headers.add(getForumPostHeader(txn, entry.getKey(), meta,
							statuses));
				}
				return headers;
			});
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public void registerRemoveForumHook(RemoveForumHook hook) {
		removeHooks.add(hook);
	}

	@Override
	public GroupCount getGroupCount(GroupId g) throws DbException {
		return messageTracker.getGroupCount(g);
	}

	@Override
	public void setReadFlag(GroupId g, MessageId m, boolean read)
			throws DbException {
		messageTracker.setReadFlag(g, m, read);
	}

	private Forum parseForum(Group g) throws FormatException {
		byte[] descriptor = g.getDescriptor();
		// Name, salt
		BdfList forum = clientHelper.toList(descriptor);
		return new Forum(g, forum.getString(0), forum.getRaw(1));
	}

	private ForumPostHeader getForumPostHeader(Transaction txn, MessageId id,
			BdfDictionary meta) throws DbException, FormatException {
		return getForumPostHeader(txn, id, meta, Collections.emptyMap());
	}

	private ForumPostHeader getForumPostHeader(Transaction txn, MessageId id,
			BdfDictionary meta, Map<AuthorId, Status> statuses)
			throws DbException, FormatException {

		long timestamp = meta.getLong(KEY_TIMESTAMP);
		MessageId parentId = null;
		if (meta.containsKey(KEY_PARENT))
			parentId = new MessageId(meta.getRaw(KEY_PARENT));
		BdfList authorList = meta.getList(KEY_AUTHOR);
		Author author = clientHelper.parseAndValidateAuthor(authorList);
		Status status = statuses.get(author.getId());
		if (status == null)
			status = identityManager.getAuthorStatus(txn, author.getId());
		boolean read = meta.getBoolean(MSG_KEY_READ);

		return new ForumPostHeader(id, parentId, timestamp, author, status,
				read);
	}

}
