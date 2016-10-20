package org.briarproject.privategroup;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
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
import org.briarproject.api.privategroup.GroupMessage;
import org.briarproject.api.privategroup.GroupMessageHeader;
import org.briarproject.api.privategroup.JoinMessageHeader;
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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

import javax.inject.Inject;

import static org.briarproject.api.identity.Author.Status.OURSELVES;
import static org.briarproject.api.privategroup.MessageType.JOIN;
import static org.briarproject.api.privategroup.MessageType.NEW_MEMBER;
import static org.briarproject.api.privategroup.MessageType.POST;
import static org.briarproject.privategroup.Constants.KEY_AUTHOR_ID;
import static org.briarproject.privategroup.Constants.KEY_AUTHOR_NAME;
import static org.briarproject.privategroup.Constants.KEY_AUTHOR_PUBLIC_KEY;
import static org.briarproject.privategroup.Constants.KEY_PARENT_ID;
import static org.briarproject.privategroup.Constants.KEY_PREVIOUS_MSG_ID;
import static org.briarproject.privategroup.Constants.KEY_READ;
import static org.briarproject.privategroup.Constants.KEY_TIMESTAMP;
import static org.briarproject.privategroup.Constants.KEY_TYPE;

public class PrivateGroupManagerImpl extends BdfIncomingMessageHook implements
		PrivateGroupManager {

	private static final Logger LOG =
			Logger.getLogger(PrivateGroupManagerImpl.class.getName());
	static final ClientId CLIENT_ID = new ClientId(
			StringUtils.fromHexString("5072697661746547726f75704d616e61"
					+ "67657220627920546f727374656e2047"));

	private final PrivateGroupFactory privateGroupFactory;
	private final IdentityManager identityManager;

	@Inject
	PrivateGroupManagerImpl(ClientHelper clientHelper,
			MetadataParser metadataParser, DatabaseComponent db,
			PrivateGroupFactory privateGroupFactory,
			IdentityManager identityManager) {
		super(db, clientHelper, metadataParser);

		this.privateGroupFactory = privateGroupFactory;
		this.identityManager = identityManager;
	}

	@Override
	public ClientId getClientId() {
		return CLIENT_ID;
	}

	@Override
	public void addPrivateGroup(PrivateGroup group,
			GroupMessage newMemberMsg, GroupMessage joinMsg)
			throws DbException {
		Transaction txn = db.startTransaction(false);
		try {
			db.addGroup(txn, group.getGroup());
			announceNewMember(txn, newMemberMsg);
			joinPrivateGroup(txn, joinMsg);
			txn.setComplete();
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
	}

	private void announceNewMember(Transaction txn, GroupMessage m)
			throws DbException, FormatException {
		BdfDictionary meta = new BdfDictionary();
		meta.put(KEY_TYPE, NEW_MEMBER.getInt());
		clientHelper.addLocalMessage(txn, m.getMessage(), meta, true);
	}

	private void joinPrivateGroup(Transaction txn, GroupMessage m)
			throws DbException, FormatException {
		BdfDictionary meta = new BdfDictionary();
		meta.put(KEY_TYPE, JOIN.getInt());
		addMessageMetadata(meta, m, true);
		clientHelper.addLocalMessage(txn, m.getMessage(), meta, true);
		trackOutgoingMessage(txn, m.getMessage());
		setPreviousMsgId(txn, m.getMessage().getGroupId(),
				m.getMessage().getId());
	}

	@Override
	public void removePrivateGroup(GroupId g) throws DbException {
		// TODO
	}

	@Override
	public MessageId getPreviousMsgId(GroupId g) throws DbException {
		MessageId previousMsgId;
		Transaction txn = db.startTransaction(true);
		try {
			previousMsgId = getPreviousMsgId(txn, g);
			txn.setComplete();
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
		byte[] previousMsgIdBytes = d.getOptionalRaw(KEY_PREVIOUS_MSG_ID);
		if (previousMsgIdBytes == null) throw new DbException();
		return new MessageId(previousMsgIdBytes);
	}

	private void setPreviousMsgId(Transaction txn, GroupId g,
			MessageId previousMsgId) throws DbException, FormatException {
		BdfDictionary d = BdfDictionary
				.of(new BdfEntry(KEY_PREVIOUS_MSG_ID, previousMsgId));
		clientHelper.mergeGroupMetadata(txn, g, d);
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
			if (m.getParent() != null) meta.put(KEY_PARENT_ID, m.getParent());
			addMessageMetadata(meta, m, true);
			clientHelper.addLocalMessage(txn, m.getMessage(), meta, true);
			trackOutgoingMessage(txn, m.getMessage());
			txn.setComplete();
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
		return new GroupMessageHeader(m.getMessage().getGroupId(),
				m.getMessage().getId(), m.getParent(),
				m.getMessage().getTimestamp(), m.getMember(), OURSELVES, true);
	}

	@Override
	public PrivateGroup getPrivateGroup(GroupId g) throws DbException {
		PrivateGroup privateGroup;
		Transaction txn = db.startTransaction(true);
		try {
			privateGroup = getPrivateGroup(txn, g);
			txn.setComplete();
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
			txn.setComplete();
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
		return false;
	}

	@Override
	public String getMessageBody(MessageId m) throws DbException {
		try {
			// TODO remove
			if (clientHelper.getMessageMetadataAsDictionary(m).getLong(KEY_TYPE) != POST.getInt())
				return "new member joined";

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
			for (Entry<MessageId, BdfDictionary> entry : metadata.entrySet()) {
				BdfDictionary meta = entry.getValue();
				if (meta.getLong(KEY_TYPE) == NEW_MEMBER.getInt())
					continue;
				byte[] idBytes = meta.getRaw(KEY_AUTHOR_ID);
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
				if (meta.getLong(KEY_TYPE) == NEW_MEMBER.getInt())
					continue;
				headers.add(getGroupMessageHeader(txn, g, entry.getKey(), meta,
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

	private GroupMessageHeader getGroupMessageHeader(Transaction txn, GroupId g,
			MessageId id, BdfDictionary meta, Map<AuthorId, Status> statuses)
			throws DbException, FormatException {

		MessageId parentId = null;
		if (meta.containsKey(KEY_PARENT_ID)) {
			parentId = new MessageId(meta.getRaw(KEY_PARENT_ID));
		}
		long timestamp = meta.getLong(KEY_TIMESTAMP);

		AuthorId authorId = new AuthorId(meta.getRaw(KEY_AUTHOR_ID));
		String name = meta.getString(KEY_AUTHOR_NAME);
		byte[] publicKey = meta.getRaw(KEY_AUTHOR_PUBLIC_KEY);
		Author author = new Author(authorId, name, publicKey);

		Status status;
		if (statuses.containsKey(authorId)) {
			status = statuses.get(authorId);
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
	protected boolean incomingMessage(Transaction txn, Message m, BdfList body,
			BdfDictionary meta) throws DbException, FormatException {

		trackIncomingMessage(txn, m);

		// TODO POST timestamp must be greater than the timestamps of the parent post, if any, and the member's previous message

		// TODO JOIN timestamp must be equal to the timestamp of the new member message.
		// TODO JOIN new_member_id must be the identifier of a NEW_MEMBER message with the same member_name and member_public_key

		return true;
	}

	private void addMessageMetadata(BdfDictionary meta, GroupMessage m,
			boolean read) {
		meta.put(KEY_TIMESTAMP, m.getMessage().getTimestamp());
		meta.put(KEY_READ, read);
		meta.put(KEY_AUTHOR_ID, m.getMember().getId());
		meta.put(KEY_AUTHOR_NAME, m.getMember().getName());
		meta.put(KEY_AUTHOR_PUBLIC_KEY, m.getMember().getPublicKey());
	}

}
