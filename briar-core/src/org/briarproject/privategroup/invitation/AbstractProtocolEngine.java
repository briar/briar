package org.briarproject.privategroup.invitation;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.MessageTracker;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.privategroup.GroupMessage;
import org.briarproject.api.privategroup.GroupMessageFactory;
import org.briarproject.api.privategroup.PrivateGroup;
import org.briarproject.api.privategroup.PrivateGroupFactory;
import org.briarproject.api.privategroup.PrivateGroupManager;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.system.Clock;

import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import static org.briarproject.privategroup.invitation.GroupInvitationConstants.GROUP_KEY_CONTACT_ID;
import static org.briarproject.privategroup.invitation.MessageType.ABORT;
import static org.briarproject.privategroup.invitation.MessageType.INVITE;
import static org.briarproject.privategroup.invitation.MessageType.JOIN;
import static org.briarproject.privategroup.invitation.MessageType.LEAVE;

@Immutable
@NotNullByDefault
abstract class AbstractProtocolEngine<S extends Session>
		implements ProtocolEngine<S> {

	protected final DatabaseComponent db;
	protected final ClientHelper clientHelper;
	protected final PrivateGroupManager privateGroupManager;
	protected final PrivateGroupFactory privateGroupFactory;
	protected final MessageTracker messageTracker;

	private final GroupMessageFactory groupMessageFactory;
	private final IdentityManager identityManager;
	private final MessageParser messageParser;
	private final MessageEncoder messageEncoder;
	private final Clock clock;

	AbstractProtocolEngine(DatabaseComponent db, ClientHelper clientHelper,
			PrivateGroupManager privateGroupManager,
			PrivateGroupFactory privateGroupFactory,
			GroupMessageFactory groupMessageFactory,
			IdentityManager identityManager, MessageParser messageParser,
			MessageEncoder messageEncoder, MessageTracker messageTracker,
			Clock clock) {
		this.db = db;
		this.clientHelper = clientHelper;
		this.privateGroupManager = privateGroupManager;
		this.privateGroupFactory = privateGroupFactory;
		this.groupMessageFactory = groupMessageFactory;
		this.identityManager = identityManager;
		this.messageParser = messageParser;
		this.messageEncoder = messageEncoder;
		this.messageTracker = messageTracker;
		this.clock = clock;
	}

	ContactId getContactId(Transaction txn, GroupId contactGroupId)
			throws DbException, FormatException {
		BdfDictionary meta = clientHelper.getGroupMetadataAsDictionary(txn,
				contactGroupId);
		return new ContactId(meta.getLong(GROUP_KEY_CONTACT_ID).intValue());
	}

	boolean isSubscribedPrivateGroup(Transaction txn, GroupId g)
			throws DbException {
		if (!db.containsGroup(txn, g)) return false;
		Group group = db.getGroup(txn, g);
		return group.getClientId().equals(PrivateGroupManager.CLIENT_ID);
	}

	boolean isValidDependency(S session, @Nullable MessageId dependency) {
		MessageId expected = session.getLastRemoteMessageId();
		if (dependency == null) return expected == null;
		return dependency.equals(expected);
	}

	void syncPrivateGroupWithContact(Transaction txn, S session, boolean sync)
			throws DbException, FormatException {
		ContactId contactId = getContactId(txn, session.getContactGroupId());
		db.setVisibleToContact(txn, contactId, session.getPrivateGroupId(),
				sync);
	}

	Message sendInviteMessage(Transaction txn, S session,
			@Nullable String message, long timestamp, byte[] signature)
			throws DbException {
		Group g = db.getGroup(txn, session.getPrivateGroupId());
		PrivateGroup privateGroup;
		try {
			privateGroup = privateGroupFactory.parsePrivateGroup(g);
		} catch (FormatException e) {
			throw new DbException(e); // Invalid group descriptor
		}
		Message m = messageEncoder.encodeInviteMessage(
				session.getContactGroupId(), privateGroup.getId(),
				timestamp, privateGroup.getName(), privateGroup.getCreator(),
				privateGroup.getSalt(), message, signature);
		sendMessage(txn, m, INVITE, privateGroup.getId(), true);
		return m;
	}

	Message sendJoinMessage(Transaction txn, S session, boolean visibleInUi)
			throws DbException {
		Message m = messageEncoder.encodeJoinMessage(
				session.getContactGroupId(), session.getPrivateGroupId(),
				getLocalTimestamp(session), session.getLastLocalMessageId());
		sendMessage(txn, m, JOIN, session.getPrivateGroupId(), visibleInUi);
		return m;
	}

	Message sendLeaveMessage(Transaction txn, S session, boolean visibleInUi)
			throws DbException {
		Message m = messageEncoder.encodeLeaveMessage(
				session.getContactGroupId(), session.getPrivateGroupId(),
				getLocalTimestamp(session), session.getLastLocalMessageId());
		sendMessage(txn, m, LEAVE, session.getPrivateGroupId(), visibleInUi);
		return m;
	}

	Message sendAbortMessage(Transaction txn, S session) throws DbException {
		Message m = messageEncoder.encodeAbortMessage(
				session.getContactGroupId(), session.getPrivateGroupId(),
				getLocalTimestamp(session));
		sendMessage(txn, m, ABORT, session.getPrivateGroupId(), false);
		return m;
	}

	void markMessageVisibleInUi(Transaction txn, MessageId m, boolean visible)
			throws DbException {
		BdfDictionary meta = new BdfDictionary();
		messageEncoder.setVisibleInUi(meta, visible);
		try {
			clientHelper.mergeMessageMetadata(txn, m, meta);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
	}

	void markMessageAvailableToAnswer(Transaction txn, MessageId m,
			boolean available) throws DbException {
		BdfDictionary meta = new BdfDictionary();
		messageEncoder.setAvailableToAnswer(meta, available);
		try {
			clientHelper.mergeMessageMetadata(txn, m, meta);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
	}

	void markInvitesUnavailableToAnswer(Transaction txn, S session)
			throws DbException, FormatException {
		GroupId privateGroupId = session.getPrivateGroupId();
		BdfDictionary query =
				messageParser.getInvitesAvailableToAnswerQuery(privateGroupId);
		Map<MessageId, BdfDictionary> results =
				clientHelper.getMessageMetadataAsDictionary(txn,
						session.getContactGroupId(), query);
		for (MessageId m : results.keySet())
			markMessageAvailableToAnswer(txn, m, false);
	}

	void subscribeToPrivateGroup(Transaction txn, MessageId inviteId)
			throws DbException, FormatException {
		InviteMessage invite = getInviteMessage(txn, inviteId);
		PrivateGroup privateGroup = privateGroupFactory.createPrivateGroup(
				invite.getGroupName(), invite.getCreator(), invite.getSalt());
		long timestamp =
				Math.max(clock.currentTimeMillis(), invite.getTimestamp() + 1);
		// TODO: Create the join message on the crypto executor
		LocalAuthor member = identityManager.getLocalAuthor(txn);
		GroupMessage joinMessage = groupMessageFactory.createJoinMessage(
				privateGroup.getId(), timestamp, member, invite.getTimestamp(),
				invite.getSignature());
		privateGroupManager
				.addPrivateGroup(txn, privateGroup, joinMessage, false);
	}

	long getLocalTimestamp(S session) {
		return Math.max(clock.currentTimeMillis(),
				Math.max(session.getLocalTimestamp(),
						session.getInviteTimestamp()) + 1);
	}

	private InviteMessage getInviteMessage(Transaction txn, MessageId m)
			throws DbException, FormatException {
		Message message = clientHelper.getMessage(txn, m);
		if (message == null) throw new DbException();
		BdfList body = clientHelper.toList(message);
		return messageParser.parseInviteMessage(message, body);
	}

	private void sendMessage(Transaction txn, Message m, MessageType type,
			GroupId privateGroupId, boolean visibleInConversation)
			throws DbException {
		BdfDictionary meta = messageEncoder.encodeMetadata(type, privateGroupId,
				m.getTimestamp(), true, true, visibleInConversation, false);
		try {
			clientHelper.addLocalMessage(txn, m, meta, true);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
	}

}
