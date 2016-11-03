package org.briarproject.privategroup;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfEntry;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataParser;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.Author.Status;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.privategroup.GroupMember;
import org.briarproject.api.privategroup.GroupMessage;
import org.briarproject.api.privategroup.GroupMessageHeader;
import org.briarproject.api.privategroup.JoinMessageHeader;
import org.briarproject.api.privategroup.MessageType;
import org.briarproject.api.privategroup.PrivateGroup;
import org.briarproject.api.privategroup.PrivateGroupFactory;
import org.briarproject.api.privategroup.PrivateGroupManager;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.clients.BdfIncomingMessageHook;
import org.briarproject.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import javax.inject.Inject;

import static org.briarproject.api.identity.Author.Status.OURSELVES;
import static org.briarproject.api.identity.Author.Status.UNVERIFIED;
import static org.briarproject.api.identity.Author.Status.VERIFIED;
import static org.briarproject.api.privategroup.MessageType.JOIN;
import static org.briarproject.api.privategroup.MessageType.POST;
import static org.briarproject.privategroup.Constants.KEY_DISSOLVED;
import static org.briarproject.privategroup.Constants.KEY_MEMBERS;
import static org.briarproject.privategroup.Constants.KEY_MEMBER_ID;
import static org.briarproject.privategroup.Constants.KEY_MEMBER_NAME;
import static org.briarproject.privategroup.Constants.KEY_MEMBER_PUBLIC_KEY;
import static org.briarproject.privategroup.Constants.KEY_PARENT_MSG_ID;
import static org.briarproject.privategroup.Constants.KEY_PREVIOUS_MSG_ID;
import static org.briarproject.privategroup.Constants.KEY_READ;
import static org.briarproject.privategroup.Constants.KEY_TIMESTAMP;
import static org.briarproject.privategroup.Constants.KEY_TYPE;

@NotNullByDefault
public class PrivateGroupManagerImpl extends BdfIncomingMessageHook implements
		PrivateGroupManager {

	private static final Logger LOG =
			Logger.getLogger(PrivateGroupManagerImpl.class.getName());
	static final ClientId CLIENT_ID = new ClientId(
			StringUtils.fromHexString("5072697661746547726f75704d616e61"
					+ "67657220627920546f727374656e2047"));

	private final PrivateGroupFactory privateGroupFactory;
	private final IdentityManager identityManager;
	private final List<PrivateGroupHook> hooks;

	@Inject
	PrivateGroupManagerImpl(ClientHelper clientHelper,
			MetadataParser metadataParser, DatabaseComponent db,
			PrivateGroupFactory privateGroupFactory,
			IdentityManager identityManager) {
		super(db, clientHelper, metadataParser);

		this.privateGroupFactory = privateGroupFactory;
		this.identityManager = identityManager;
		hooks = new CopyOnWriteArrayList<PrivateGroupHook>();
	}

	@Override
	public ClientId getClientId() {
		return CLIENT_ID;
	}

	@Override
	public void addPrivateGroup(PrivateGroup group, GroupMessage joinMsg)
			throws DbException {
		Transaction txn = db.startTransaction(false);
		try {
			db.addGroup(txn, group.getGroup());
			BdfDictionary meta = BdfDictionary.of(
					new BdfEntry(KEY_MEMBERS, new BdfList()),
					new BdfEntry(KEY_DISSOLVED, false)
			);
			clientHelper.mergeGroupMetadata(txn, group.getId(), meta);
			joinPrivateGroup(txn, joinMsg);
			db.commitTransaction(txn);
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
	}

	private void joinPrivateGroup(Transaction txn, GroupMessage m)
			throws DbException, FormatException {
		BdfDictionary meta = new BdfDictionary();
		meta.put(KEY_TYPE, JOIN.getInt());
		addMessageMetadata(meta, m, true);
		clientHelper.addLocalMessage(txn, m.getMessage(), meta, true);
		trackOutgoingMessage(txn, m.getMessage());
		addMember(txn, m.getMessage().getGroupId(), m.getMember());
		setPreviousMsgId(txn, m.getMessage().getGroupId(),
				m.getMessage().getId());
	}

	@Override
	public void removePrivateGroup(GroupId g) throws DbException {
		Transaction txn = db.startTransaction(false);
		try {
			for (PrivateGroupHook hook : hooks) {
				hook.removingGroup(txn, g);
			}
			Group group = db.getGroup(txn, g);
			db.removeGroup(txn, group);
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public MessageId getPreviousMsgId(GroupId g) throws DbException {
		MessageId previousMsgId;
		Transaction txn = db.startTransaction(true);
		try {
			previousMsgId = getPreviousMsgId(txn, g);
			db.commitTransaction(txn);
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
		return previousMsgId;
	}

	private MessageId getPreviousMsgId(Transaction txn, GroupId g)
			throws DbException, FormatException {
		BdfDictionary d = clientHelper.getGroupMetadataAsDictionary(txn, g);
		byte[] previousMsgIdBytes = d.getRaw(KEY_PREVIOUS_MSG_ID);
		return new MessageId(previousMsgIdBytes);
	}

	private void setPreviousMsgId(Transaction txn, GroupId g,
			MessageId previousMsgId) throws DbException, FormatException {
		BdfDictionary d = BdfDictionary
				.of(new BdfEntry(KEY_PREVIOUS_MSG_ID, previousMsgId));
		clientHelper.mergeGroupMetadata(txn, g, d);
	}

	@Override
	public void markGroupDissolved(Transaction txn, GroupId g)
			throws DbException {
		BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(KEY_DISSOLVED, true)
		);
		try {
			clientHelper.mergeGroupMetadata(txn, g, meta);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public long getMessageTimestamp(MessageId id) throws DbException {
		try {
			BdfDictionary d = clientHelper.getMessageMetadataAsDictionary(id);
			return d.getLong(KEY_TIMESTAMP);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public GroupMessageHeader addLocalMessage(GroupMessage m)
			throws DbException {
		Transaction txn = db.startTransaction(false);
		try {
			BdfDictionary meta = new BdfDictionary();
			meta.put(KEY_TYPE, POST.getInt());
			if (m.getParent() != null) meta.put(KEY_PARENT_MSG_ID, m.getParent());
			addMessageMetadata(meta, m, true);
			clientHelper.addLocalMessage(txn, m.getMessage(), meta, true);
			setPreviousMsgId(txn, m.getMessage().getGroupId(),
					m.getMessage().getId());
			trackOutgoingMessage(txn, m.getMessage());
			db.commitTransaction(txn);
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
		return new GroupMessageHeader(m.getMessage().getGroupId(),
				m.getMessage().getId(), m.getParent(),
				m.getMessage().getTimestamp(), m.getMember(), OURSELVES, true);
	}

	private void addMessageMetadata(BdfDictionary meta, GroupMessage m,
			boolean read) {
		meta.put(KEY_TIMESTAMP, m.getMessage().getTimestamp());
		meta.put(KEY_READ, read);
		meta.put(KEY_MEMBER_ID, m.getMember().getId());
		meta.put(KEY_MEMBER_NAME, m.getMember().getName());
		meta.put(KEY_MEMBER_PUBLIC_KEY, m.getMember().getPublicKey());
	}

	@Override
	public PrivateGroup getPrivateGroup(GroupId g) throws DbException {
		PrivateGroup privateGroup;
		Transaction txn = db.startTransaction(true);
		try {
			privateGroup = getPrivateGroup(txn, g);
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
		return privateGroup;
	}

	@Override
	public PrivateGroup getPrivateGroup(Transaction txn, GroupId g)
			throws DbException {
		try {
			Group group = db.getGroup(txn, g);
			return privateGroupFactory.parsePrivateGroup(group);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public Collection<PrivateGroup> getPrivateGroups() throws DbException {
		Collection<Group> groups;
		Transaction txn = db.startTransaction(true);
		try {
			groups = db.getGroups(txn, getClientId());
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
		try {
			Collection<PrivateGroup> privateGroups =
					new ArrayList<PrivateGroup>(groups.size());
			for (Group g : groups) {
				privateGroups.add(privateGroupFactory.parsePrivateGroup(g));
			}
			return privateGroups;
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public boolean isDissolved(GroupId g) throws DbException {
		try {
			BdfDictionary meta = clientHelper.getGroupMetadataAsDictionary(g);
			return meta.getBoolean(KEY_DISSOLVED);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public String getMessageBody(MessageId m) throws DbException {
		try {
			// type(0), member_name(1), member_public_key(2), parent_id(3),
			// previous_message_id(4), content(5), signature(6)
			return clientHelper.getMessageAsList(m).getString(5);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public Collection<GroupMessageHeader> getHeaders(GroupId g)
			throws DbException {
		Collection<GroupMessageHeader> headers =
				new ArrayList<GroupMessageHeader>();
		Transaction txn = db.startTransaction(true);
		try {
			Map<MessageId, BdfDictionary> metadata =
					clientHelper.getMessageMetadataAsDictionary(txn, g);
			// get all authors we need to get the status for
			Set<AuthorId> authors = new HashSet<AuthorId>();
			for (BdfDictionary meta : metadata.values()) {
				byte[] idBytes = meta.getRaw(KEY_MEMBER_ID);
				authors.add(new AuthorId(idBytes));
			}
			// get statuses for all authors
			Map<AuthorId, Status> statuses = new HashMap<AuthorId, Status>();
			for (AuthorId id : authors) {
				statuses.put(id, identityManager.getAuthorStatus(txn, id));
			}
			// Parse the metadata
			for (Entry<MessageId, BdfDictionary> entry : metadata.entrySet()) {
				BdfDictionary meta = entry.getValue();
				headers.add(getGroupMessageHeader(txn, g, entry.getKey(), meta,
						statuses));
			}
			db.commitTransaction(txn);
			return headers;
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
	}

	private GroupMessageHeader getGroupMessageHeader(Transaction txn, GroupId g,
			MessageId id, BdfDictionary meta, Map<AuthorId, Status> statuses)
			throws DbException, FormatException {

		MessageId parentId = null;
		if (meta.containsKey(KEY_PARENT_MSG_ID)) {
			parentId = new MessageId(meta.getRaw(KEY_PARENT_MSG_ID));
		}
		long timestamp = meta.getLong(KEY_TIMESTAMP);

		Author author = getAuthor(meta);
		Status status;
		if (statuses.containsKey(author.getId())) {
			status = statuses.get(author.getId());
		} else {
			status = identityManager.getAuthorStatus(txn, author.getId());
		}
		boolean read = meta.getBoolean(KEY_READ);

		if (meta.getLong(KEY_TYPE) == JOIN.getInt()) {
			return new JoinMessageHeader(g, id, parentId, timestamp, author,
					status, read);
		}
		return new GroupMessageHeader(g, id, parentId, timestamp, author,
				status, read);
	}

	@Override
	public Collection<GroupMember> getMembers(GroupId g) throws DbException {
		Transaction txn = db.startTransaction(true);
		try {
			Collection<GroupMember> members = new ArrayList<GroupMember>();
			Collection<Author> authors = getMembers(txn, g);
			for (Author a : authors) {
				Status status = identityManager.getAuthorStatus(txn, a.getId());
				boolean shared = false;
				if (status == VERIFIED || status == UNVERIFIED) {
					Collection<ContactId> contacts =
							db.getContacts(txn, a.getId());
					if (contacts.size() != 1) throw new DbException();
					ContactId c = contacts.iterator().next();
					shared = db.isVisibleToContact(txn, c, g);
				}
				members.add(new GroupMember(a, status, shared));
			}
			db.commitTransaction(txn);
			return members;
		} finally {
			db.endTransaction(txn);
		}
	}

	private Collection<Author> getMembers(Transaction txn, GroupId g)
			throws DbException {
		try {
			Collection<Author> members = new ArrayList<Author>();
			BdfDictionary meta =
					clientHelper.getGroupMetadataAsDictionary(txn, g);
			BdfList list = meta.getList(KEY_MEMBERS);
			for (Object o : list) {
				BdfDictionary d = (BdfDictionary) o;
				Author member = getAuthor(d);
				members.add(member);
			}
			return members;
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public boolean isMember(Transaction txn, GroupId g, Author a)
			throws DbException {
		for (Author member : getMembers(txn, g)) {
			if (member.equals(a)) return true;
		}
		return false;
	}

	@Override
	public void registerPrivateGroupHook(PrivateGroupHook hook) {
		hooks.add(hook);
	}

	@Override
	protected boolean incomingMessage(Transaction txn, Message m, BdfList body,
			BdfDictionary meta) throws DbException, FormatException {

		long timestamp = meta.getLong(KEY_TIMESTAMP);
		MessageType type =
				MessageType.valueOf(meta.getLong(KEY_TYPE).intValue());
		switch (type) {
			case JOIN:
				addMember(txn, m.getGroupId(), getAuthor(meta));
				trackIncomingMessage(txn, m);
				return true;
			case POST:
				// timestamp must be greater than the timestamps of parent post
				byte[] parentIdBytes = meta.getOptionalRaw(KEY_PARENT_MSG_ID);
				if (parentIdBytes != null) {
					MessageId parentId = new MessageId(parentIdBytes);
					BdfDictionary parentMeta = clientHelper
							.getMessageMetadataAsDictionary(txn, parentId);
					if (timestamp <= parentMeta.getLong(KEY_TIMESTAMP)) {
						// FIXME throw new InvalidMessageException() (#643)
						db.deleteMessage(txn, m.getId());
						return false;
					}
					MessageType parentType = MessageType
							.valueOf(parentMeta.getLong(KEY_TYPE).intValue());
					if (parentType != POST) {
						// FIXME throw new InvalidMessageException() (#643)
						db.deleteMessage(txn, m.getId());
						return false;
					}
				}
				// and the member's previous message
				byte[] previousMsgIdBytes = meta.getRaw(KEY_PREVIOUS_MSG_ID);
				MessageId previousMsgId = new MessageId(previousMsgIdBytes);
				BdfDictionary previousMeta = clientHelper
						.getMessageMetadataAsDictionary(txn, previousMsgId);
				if (timestamp <= previousMeta.getLong(KEY_TIMESTAMP)) {
					// FIXME throw new InvalidMessageException() (#643)
					db.deleteMessage(txn, m.getId());
					return false;
				}
				// previous message must be from same member
				if (!Arrays.equals(meta.getRaw(KEY_MEMBER_ID),
						previousMeta.getRaw(KEY_MEMBER_ID))) {
					// FIXME throw new InvalidMessageException() (#643)
					db.deleteMessage(txn, m.getId());
					return false;
				}
				// previous message must be a POST or JOIN
				MessageType previousType = MessageType
						.valueOf(previousMeta.getLong(KEY_TYPE).intValue());
				if (previousType != JOIN && previousType != POST) {
					// FIXME throw new InvalidMessageException() (#643)
					db.deleteMessage(txn, m.getId());
					return false;
				}
				trackIncomingMessage(txn, m);
				return true;
			default:
				// the validator should only let valid types pass
				throw new RuntimeException("Unknown MessageType");
		}
	}

	private void addMember(Transaction txn, GroupId g, Author a)
			throws DbException, FormatException {

		BdfDictionary meta = clientHelper.getGroupMetadataAsDictionary(txn, g);
		BdfList members = meta.getList(KEY_MEMBERS);
		members.add(BdfDictionary.of(
				new BdfEntry(KEY_MEMBER_ID, a.getId()),
				new BdfEntry(KEY_MEMBER_NAME, a.getName()),
				new BdfEntry(KEY_MEMBER_PUBLIC_KEY, a.getPublicKey())
		));
		clientHelper.mergeGroupMetadata(txn, g, meta);
	}

	private Author getAuthor(BdfDictionary meta) throws FormatException {
		AuthorId authorId = new AuthorId(meta.getRaw(KEY_MEMBER_ID));
		String name = meta.getString(KEY_MEMBER_NAME);
		byte[] publicKey = meta.getRaw(KEY_MEMBER_PUBLIC_KEY);
		return new Author(authorId, name, publicKey);
	}

}
