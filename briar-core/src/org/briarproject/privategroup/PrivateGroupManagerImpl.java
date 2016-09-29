package org.briarproject.privategroup;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataParser;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.privategroup.GroupMessage;
import org.briarproject.api.privategroup.GroupMessageHeader;
import org.briarproject.api.privategroup.PrivateGroup;
import org.briarproject.api.privategroup.PrivateGroupFactory;
import org.briarproject.api.privategroup.PrivateGroupManager;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.clients.BdfIncomingMessageHook;
import org.briarproject.util.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

import javax.inject.Inject;

public class PrivateGroupManagerImpl extends BdfIncomingMessageHook implements
		PrivateGroupManager {

	private static final Logger LOG =
			Logger.getLogger(PrivateGroupManagerImpl.class.getName());
	static final ClientId CLIENT_ID = new ClientId(
			StringUtils.fromHexString("5072697661746547726f75704d616e61"
					+ "67657220627920546f727374656e2047"));

	private final IdentityManager identityManager;
	private final PrivateGroupFactory privateGroupFactory;

	@Inject
	PrivateGroupManagerImpl(ClientHelper clientHelper,
			MetadataParser metadataParser, DatabaseComponent db,
			IdentityManager identityManager,
			PrivateGroupFactory privateGroupFactory) {
		super(db, clientHelper, metadataParser);

		this.identityManager = identityManager;
		this.privateGroupFactory = privateGroupFactory;
	}

	@NotNull
	@Override
	public ClientId getClientId() {
		return CLIENT_ID;
	}

	@Override
	public void removePrivateGroup(GroupId g) throws DbException {

	}

	@Override
	public void addLocalMessage(GroupMessage m) throws DbException {
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
	}

	@NotNull
	@Override
	public PrivateGroup getPrivateGroup(GroupId g) throws DbException {
		Author a = identityManager.getLocalAuthor();
		return privateGroupFactory.createPrivateGroup("todo", a);
	}

	@NotNull
	@Override
	public PrivateGroup getPrivateGroup(Transaction txn, GroupId g)
			throws DbException {
		Author a = identityManager.getLocalAuthor(txn);
		return privateGroupFactory.createPrivateGroup("todo", a);
	}

	@NotNull
	@Override
	public Collection<PrivateGroup> getPrivateGroups() throws DbException {
		return Collections.emptyList();
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
