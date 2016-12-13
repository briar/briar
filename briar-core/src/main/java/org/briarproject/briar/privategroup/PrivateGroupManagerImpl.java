package org.briarproject.briar.privategroup;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.Event;
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
import org.briarproject.briar.api.client.ProtocolStateException;
import org.briarproject.briar.api.privategroup.GroupMember;
import org.briarproject.briar.api.privategroup.GroupMessage;
import org.briarproject.briar.api.privategroup.GroupMessageHeader;
import org.briarproject.briar.api.privategroup.JoinMessageHeader;
import org.briarproject.briar.api.privategroup.MessageType;
import org.briarproject.briar.api.privategroup.PrivateGroup;
import org.briarproject.briar.api.privategroup.PrivateGroupFactory;
import org.briarproject.briar.api.privategroup.PrivateGroupManager;
import org.briarproject.briar.api.privategroup.Visibility;
import org.briarproject.briar.api.privategroup.event.ContactRelationshipRevealedEvent;
import org.briarproject.briar.api.privategroup.event.GroupDissolvedEvent;
import org.briarproject.briar.api.privategroup.event.GroupMessageAddedEvent;
import org.briarproject.briar.client.BdfIncomingMessageHook;

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

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import static org.briarproject.bramble.api.identity.Author.Status.OURSELVES;
import static org.briarproject.bramble.api.identity.Author.Status.UNVERIFIED;
import static org.briarproject.bramble.api.identity.Author.Status.VERIFIED;
import static org.briarproject.briar.api.privategroup.MessageType.JOIN;
import static org.briarproject.briar.api.privategroup.MessageType.POST;
import static org.briarproject.briar.api.privategroup.Visibility.INVISIBLE;
import static org.briarproject.briar.api.privategroup.Visibility.REVEALED_BY_CONTACT;
import static org.briarproject.briar.api.privategroup.Visibility.REVEALED_BY_US;
import static org.briarproject.briar.api.privategroup.Visibility.VISIBLE;
import static org.briarproject.briar.privategroup.GroupConstants.GROUP_KEY_CREATOR_ID;
import static org.briarproject.briar.privategroup.GroupConstants.GROUP_KEY_DISSOLVED;
import static org.briarproject.briar.privategroup.GroupConstants.GROUP_KEY_MEMBERS;
import static org.briarproject.briar.privategroup.GroupConstants.GROUP_KEY_OUR_GROUP;
import static org.briarproject.briar.privategroup.GroupConstants.GROUP_KEY_VISIBILITY;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_INITIAL_JOIN_MSG;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_MEMBER_ID;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_MEMBER_NAME;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_MEMBER_PUBLIC_KEY;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_PARENT_MSG_ID;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_PREVIOUS_MSG_ID;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_READ;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_TIMESTAMP;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_TYPE;

@ThreadSafe
@NotNullByDefault
class PrivateGroupManagerImpl extends BdfIncomingMessageHook
		implements PrivateGroupManager {

	private final PrivateGroupFactory privateGroupFactory;
	private final ContactManager contactManager;
	private final IdentityManager identityManager;
	private final MessageTracker messageTracker;
	private final List<PrivateGroupHook> hooks;

	@Inject
	PrivateGroupManagerImpl(ClientHelper clientHelper,
			MetadataParser metadataParser, DatabaseComponent db,
			PrivateGroupFactory privateGroupFactory,
			ContactManager contactManager, IdentityManager identityManager,
			MessageTracker messageTracker) {
		super(db, clientHelper, metadataParser);
		this.privateGroupFactory = privateGroupFactory;
		this.contactManager = contactManager;
		this.identityManager = identityManager;
		this.messageTracker = messageTracker;
		hooks = new CopyOnWriteArrayList<PrivateGroupHook>();
	}

	@Override
	public void addPrivateGroup(PrivateGroup group, GroupMessage joinMsg,
			boolean creator) throws DbException {
		Transaction txn = db.startTransaction(false);
		try {
			addPrivateGroup(txn, group, joinMsg, creator);
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public void addPrivateGroup(Transaction txn, PrivateGroup group,
			GroupMessage joinMsg, boolean creator) throws DbException {
		try {
			db.addGroup(txn, group.getGroup());
			AuthorId creatorId = group.getCreator().getId();
			BdfDictionary meta = BdfDictionary.of(
					new BdfEntry(GROUP_KEY_MEMBERS, new BdfList()),
					new BdfEntry(GROUP_KEY_CREATOR_ID, creatorId),
					new BdfEntry(GROUP_KEY_OUR_GROUP, creator),
					new BdfEntry(GROUP_KEY_DISSOLVED, false)
			);
			clientHelper.mergeGroupMetadata(txn, group.getId(), meta);
			joinPrivateGroup(txn, joinMsg, creator);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private void joinPrivateGroup(Transaction txn, GroupMessage m,
			boolean creator) throws DbException, FormatException {
		BdfDictionary meta = new BdfDictionary();
		meta.put(KEY_TYPE, JOIN.getInt());
		meta.put(KEY_INITIAL_JOIN_MSG, creator);
		addMessageMetadata(meta, m, true);
		clientHelper.addLocalMessage(txn, m.getMessage(), meta, true);
		messageTracker.trackOutgoingMessage(txn, m.getMessage());
		addMember(txn, m.getMessage().getGroupId(), m.getMember(), VISIBLE);
		setPreviousMsgId(txn, m.getMessage().getGroupId(),
				m.getMessage().getId());
		attachJoinMessageAddedEvent(txn, m.getMessage(), meta, true, VISIBLE);
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
				new BdfEntry(GROUP_KEY_DISSOLVED, true)
		);
		try {
			clientHelper.mergeGroupMetadata(txn, g, meta);
		} catch (FormatException e) {
			throw new DbException(e);
		}
		Event e = new GroupDissolvedEvent(g);
		txn.attach(e);
	}

	@Override
	public GroupMessageHeader addLocalMessage(GroupMessage m)
			throws DbException {
		Transaction txn = db.startTransaction(false);
		try {
			// store message and metadata
			BdfDictionary meta = new BdfDictionary();
			meta.put(KEY_TYPE, POST.getInt());
			if (m.getParent() != null)
				meta.put(KEY_PARENT_MSG_ID, m.getParent());
			addMessageMetadata(meta, m, true);
			GroupId g = m.getMessage().getGroupId();
			clientHelper.addLocalMessage(txn, m.getMessage(), meta, true);

			// track message
			setPreviousMsgId(txn, g, m.getMessage().getId());
			messageTracker.trackOutgoingMessage(txn, m.getMessage());

			// broadcast event
			attachGroupMessageAddedEvent(txn, m.getMessage(), meta, true);

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
			groups = db.getGroups(txn, CLIENT_ID);
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
			return meta.getBoolean(GROUP_KEY_DISSOLVED);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public String getMessageBody(MessageId m) throws DbException {
		try {
			// type(0), member_name(1), member_public_key(2), parent_id(3),
			// previous_message_id(4), content(5), signature(6)
			BdfList body = clientHelper.getMessageAsList(m);
			if (body == null) throw new DbException();
			return body.getString(5);
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
			// get current visibilities for join messages
			Map<Author, Visibility> visibilities = getMembers(txn, g);
			// parse the metadata
			for (Entry<MessageId, BdfDictionary> entry : metadata.entrySet()) {
				BdfDictionary meta = entry.getValue();
				if (meta.getLong(KEY_TYPE) == JOIN.getInt()) {
					Author member = getAuthor(meta);
					Visibility v = visibilities.get(member);
					headers.add(
							getJoinMessageHeader(txn, g, entry.getKey(), meta,
									statuses, v));
				} else {
					headers.add(
							getGroupMessageHeader(txn, g, entry.getKey(), meta,
									statuses));
				}
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

		return new GroupMessageHeader(g, id, parentId, timestamp, author,
				status, read);
	}

	private JoinMessageHeader getJoinMessageHeader(Transaction txn, GroupId g,
			MessageId id, BdfDictionary meta, Map<AuthorId, Status> statuses,
			Visibility v) throws DbException, FormatException {

		GroupMessageHeader header =
				getGroupMessageHeader(txn, g, id, meta, statuses);
		boolean creator = meta.getBoolean(KEY_INITIAL_JOIN_MSG);
		return new JoinMessageHeader(header, v, creator);
	}

	@Override
	public Collection<GroupMember> getMembers(GroupId g) throws DbException {
		Transaction txn = db.startTransaction(true);
		try {
			Collection<GroupMember> members = new ArrayList<GroupMember>();
			Map<Author, Visibility> authors = getMembers(txn, g);
			LocalAuthor la = identityManager.getLocalAuthor(txn);
			PrivateGroup privateGroup = getPrivateGroup(txn, g);
			for (Entry<Author, Visibility> m : authors.entrySet()) {
				Author a = m.getKey();
				Status status = identityManager.getAuthorStatus(txn, a.getId());
				Visibility v = m.getValue();
				ContactId c = null;
				if (v != INVISIBLE &&
						(status == VERIFIED || status == UNVERIFIED)) {
					c = contactManager.getContact(txn, a.getId(), la.getId())
							.getId();
				}
				boolean isCreator = privateGroup.getCreator().equals(a);
				members.add(new GroupMember(a, status, isCreator, c, v));
			}
			db.commitTransaction(txn);
			return members;
		} finally {
			db.endTransaction(txn);
		}
	}

	private Map<Author, Visibility> getMembers(Transaction txn, GroupId g)
			throws DbException {
		try {
			BdfDictionary meta =
					clientHelper.getGroupMetadataAsDictionary(txn, g);
			BdfList list = meta.getList(GROUP_KEY_MEMBERS);
			Map<Author, Visibility> members =
					new HashMap<Author, Visibility>(list.size());
			for (int i = 0; i < list.size(); i++) {
				BdfDictionary d = list.getDictionary(i);
				Author member = getAuthor(d);
				Visibility v = getVisibility(d);
				members.put(member, v);
			}
			return members;
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public boolean isMember(Transaction txn, GroupId g, Author a)
			throws DbException {
		for (Author member : getMembers(txn, g).keySet()) {
			if (member.equals(a)) return true;
		}
		return false;
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

	@Override
	public void relationshipRevealed(Transaction txn, GroupId g, AuthorId a,
			boolean byContact) throws FormatException, DbException {
		BdfDictionary meta = clientHelper.getGroupMetadataAsDictionary(txn, g);
		BdfList members = meta.getList(GROUP_KEY_MEMBERS);
		Visibility v = INVISIBLE;
		boolean foundMember = false, changed = false;
		for (int i = 0; i < members.size(); i++) {
			BdfDictionary d = members.getDictionary(i);
			AuthorId memberId = new AuthorId(d.getRaw(KEY_MEMBER_ID));
			if (a.equals(memberId)) {
				foundMember = true;
				// Don't update the visibility if the contact is already visible
				if (getVisibility(d) == INVISIBLE) {
					changed = true;
					v = byContact ? REVEALED_BY_CONTACT : REVEALED_BY_US;
					d.put(GROUP_KEY_VISIBILITY, v.getInt());
				}
				break;
			}
		}
		if (!foundMember) throw new ProtocolStateException();
		if (changed) {
			clientHelper.mergeGroupMetadata(txn, g, meta);
			LocalAuthor la = identityManager.getLocalAuthor(txn);
			ContactId c = contactManager.getContact(txn, a, la.getId()).getId();
			Event e = new ContactRelationshipRevealedEvent(g, a, c, v);
			txn.attach(e);
		}
	}

	@Override
	public void registerPrivateGroupHook(PrivateGroupHook hook) {
		hooks.add(hook);
	}

	@Override
	protected boolean incomingMessage(Transaction txn, Message m, BdfList body,
			BdfDictionary meta) throws DbException, FormatException {

		MessageType type =
				MessageType.valueOf(meta.getLong(KEY_TYPE).intValue());
		switch (type) {
			case JOIN:
				handleJoinMessage(txn, m, meta);
				return true;
			case POST:
				handleGroupMessage(txn, m, meta);
				return true;
			default:
				// the validator should only let valid types pass
				throw new RuntimeException("Unknown MessageType");
		}
	}

	private void handleJoinMessage(Transaction txn, Message m,
			BdfDictionary meta) throws FormatException, DbException {
		// find out if contact relationship is visible and then add new member
		Author member = getAuthor(meta);
		BdfDictionary groupMeta = clientHelper
				.getGroupMetadataAsDictionary(txn, m.getGroupId());
		boolean ourGroup = groupMeta.getBoolean(GROUP_KEY_OUR_GROUP);
		Visibility v = VISIBLE;
		if (!ourGroup) {
			AuthorId creatorId = new AuthorId(
					groupMeta.getRaw(GROUP_KEY_CREATOR_ID));
			if (!creatorId.equals(member.getId()))
				v = INVISIBLE;
		}
		addMember(txn, m.getGroupId(), member, v);
		// track message and broadcast event
		messageTracker.trackIncomingMessage(txn, m);
		attachJoinMessageAddedEvent(txn, m, meta, false, v);
	}

	private void handleGroupMessage(Transaction txn, Message m,
			BdfDictionary meta) throws FormatException, DbException {
		// timestamp must be greater than the timestamps of parent post
		long timestamp = meta.getLong(KEY_TIMESTAMP);
		byte[] parentIdBytes = meta.getOptionalRaw(KEY_PARENT_MSG_ID);
		if (parentIdBytes != null) {
			MessageId parentId = new MessageId(parentIdBytes);
			BdfDictionary parentMeta = clientHelper
					.getMessageMetadataAsDictionary(txn, parentId);
			if (timestamp <= parentMeta.getLong(KEY_TIMESTAMP))
				throw new FormatException();
			MessageType parentType = MessageType
					.valueOf(parentMeta.getLong(KEY_TYPE).intValue());
			if (parentType != POST)
				throw new FormatException();
		}
		// and the member's previous message
		byte[] previousMsgIdBytes = meta.getRaw(KEY_PREVIOUS_MSG_ID);
		MessageId previousMsgId = new MessageId(previousMsgIdBytes);
		BdfDictionary previousMeta = clientHelper
				.getMessageMetadataAsDictionary(txn, previousMsgId);
		if (timestamp <= previousMeta.getLong(KEY_TIMESTAMP))
			throw new FormatException();
		// previous message must be from same member
		if (!Arrays.equals(meta.getRaw(KEY_MEMBER_ID),
				previousMeta.getRaw(KEY_MEMBER_ID)))
			throw new FormatException();
		// previous message must be a POST or JOIN
		MessageType previousType = MessageType
				.valueOf(previousMeta.getLong(KEY_TYPE).intValue());
		if (previousType != JOIN && previousType != POST)
			throw new FormatException();
		// track message and broadcast event
		messageTracker.trackIncomingMessage(txn, m);
		attachGroupMessageAddedEvent(txn, m, meta, false);
	}

	private void attachGroupMessageAddedEvent(Transaction txn, Message m,
			BdfDictionary meta, boolean local)
			throws DbException, FormatException {
		GroupMessageHeader h =
				getGroupMessageHeader(txn, m.getGroupId(), m.getId(), meta,
						Collections.<AuthorId, Status>emptyMap());
		Event e = new GroupMessageAddedEvent(m.getGroupId(), h, local);
		txn.attach(e);
	}

	private void attachJoinMessageAddedEvent(Transaction txn, Message m,
			BdfDictionary meta, boolean local, Visibility v)
			throws DbException, FormatException {
		JoinMessageHeader h =
				getJoinMessageHeader(txn, m.getGroupId(), m.getId(), meta,
						Collections.<AuthorId, Status>emptyMap(), v);
		Event e = new GroupMessageAddedEvent(m.getGroupId(), h, local);
		txn.attach(e);
	}

	private void addMember(Transaction txn, GroupId g, Author a, Visibility v)
			throws DbException, FormatException {

		BdfDictionary meta = clientHelper.getGroupMetadataAsDictionary(txn, g);
		BdfList members = meta.getList(GROUP_KEY_MEMBERS);
		members.add(BdfDictionary.of(
				new BdfEntry(KEY_MEMBER_ID, a.getId()),
				new BdfEntry(KEY_MEMBER_NAME, a.getName()),
				new BdfEntry(KEY_MEMBER_PUBLIC_KEY, a.getPublicKey()),
				new BdfEntry(GROUP_KEY_VISIBILITY, v.getInt())
		));
		clientHelper.mergeGroupMetadata(txn, g, meta);
		for (PrivateGroupHook hook : hooks) {
			hook.addingMember(txn, g, a);
		}
	}

	private Author getAuthor(BdfDictionary meta) throws FormatException {
		AuthorId authorId = new AuthorId(meta.getRaw(KEY_MEMBER_ID));
		String name = meta.getString(KEY_MEMBER_NAME);
		byte[] publicKey = meta.getRaw(KEY_MEMBER_PUBLIC_KEY);
		return new Author(authorId, name, publicKey);
	}

	private Visibility getVisibility(BdfDictionary meta)
			throws FormatException {
		return Visibility
				.valueOf(meta.getLong(GROUP_KEY_VISIBILITY).intValue());
	}

}
