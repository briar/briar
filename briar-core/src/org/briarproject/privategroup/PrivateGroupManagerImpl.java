package org.briarproject.privategroup;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataParser;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.privategroup.GroupMessage;
import org.briarproject.api.privategroup.GroupMessageFactory;
import org.briarproject.api.privategroup.GroupMessageHeader;
import org.briarproject.api.privategroup.PrivateGroup;
import org.briarproject.api.privategroup.PrivateGroupFactory;
import org.briarproject.api.privategroup.PrivateGroupManager;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.system.Clock;
import org.briarproject.clients.BdfIncomingMessageHook;
import org.briarproject.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

import javax.inject.Inject;

import static org.briarproject.api.identity.Author.Status.OURSELVES;

public class PrivateGroupManagerImpl extends BdfIncomingMessageHook implements
		PrivateGroupManager {

	private static final Logger LOG =
			Logger.getLogger(PrivateGroupManagerImpl.class.getName());
	static final ClientId CLIENT_ID = new ClientId(
			StringUtils.fromHexString("5072697661746547726f75704d616e61"
					+ "67657220627920546f727374656e2047"));

	private final IdentityManager identityManager;
	private final PrivateGroupFactory privateGroupFactory;
	private final GroupMessageFactory groupMessageFactory;
	private final Clock clock;

	@Inject
	PrivateGroupManagerImpl(ClientHelper clientHelper,
			MetadataParser metadataParser, DatabaseComponent db,
			IdentityManager identityManager,
			PrivateGroupFactory privateGroupFactory,
			GroupMessageFactory groupMessageFactory, Clock clock) {
		super(db, clientHelper, metadataParser);

		this.identityManager = identityManager;
		this.privateGroupFactory = privateGroupFactory;
		this.groupMessageFactory = groupMessageFactory;
		this.clock = clock;
	}

	@NotNull
	@Override
	public ClientId getClientId() {
		return CLIENT_ID;
	}

	@Override
	public GroupId addPrivateGroup(String name) throws DbException {
		PrivateGroup group;
		Transaction txn = db.startTransaction(false);
		try {
			LocalAuthor a = identityManager.getLocalAuthor(txn);
			group = privateGroupFactory.createPrivateGroup(name, a);
			db.addGroup(txn, group.getGroup());
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
		return group.getId();
	}

	@Override
	public void removePrivateGroup(GroupId g) throws DbException {

	}

	@Override
	public GroupMessage createLocalMessage(GroupId groupId, String body,
			long timestamp, @Nullable MessageId parentId, LocalAuthor author) {
		try {
			return groupMessageFactory
					.createGroupMessage(groupId, timestamp, parentId, author,
							body);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public GroupMessageHeader addLocalMessage(GroupMessage m)
			throws DbException {
		Transaction txn = db.startTransaction(false);
		try {
			BdfDictionary meta = new BdfDictionary();
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
				m.getMessage().getTimestamp(), m.getAuthor(), OURSELVES, true);
	}

	@NotNull
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

	@NotNull
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

	@NotNull
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

	@NotNull
	@Override
	public String getMessageBody(MessageId m) throws DbException {
		return "empty";
	}

	@NotNull
	@Override
	public Collection<GroupMessageHeader> getHeaders(GroupId g)
			throws DbException {

		return Collections.emptyList();
	}

	@Override
	protected boolean incomingMessage(Transaction txn, Message m, BdfList body,
			BdfDictionary meta) throws DbException, FormatException {

		trackIncomingMessage(txn, m);

		return true;
	}

}
