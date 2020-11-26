package org.briarproject.briar.privategroup.invitation;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.Group.Visibility;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.versioning.ClientVersioningManager;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.privategroup.GroupMessage;
import org.briarproject.briar.api.privategroup.GroupMessageFactory;
import org.briarproject.briar.api.privategroup.PrivateGroup;
import org.briarproject.briar.api.privategroup.PrivateGroupFactory;
import org.briarproject.briar.api.privategroup.PrivateGroupManager;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationManager;

import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.NO_AUTO_DELETE_TIMER;
import static org.briarproject.briar.privategroup.invitation.MessageType.ABORT;
import static org.briarproject.briar.privategroup.invitation.MessageType.INVITE;
import static org.briarproject.briar.privategroup.invitation.MessageType.JOIN;
import static org.briarproject.briar.privategroup.invitation.MessageType.LEAVE;

@Immutable
@NotNullByDefault
abstract class AbstractProtocolEngine<S extends Session<?>>
		implements ProtocolEngine<S> {

	protected final DatabaseComponent db;
	protected final ClientHelper clientHelper;
	protected final PrivateGroupManager privateGroupManager;
	protected final PrivateGroupFactory privateGroupFactory;
	protected final MessageTracker messageTracker;

	private final ClientVersioningManager clientVersioningManager;
	private final GroupMessageFactory groupMessageFactory;
	private final IdentityManager identityManager;
	private final MessageParser messageParser;
	private final MessageEncoder messageEncoder;
	private final Clock clock;

	AbstractProtocolEngine(DatabaseComponent db, ClientHelper clientHelper,
			ClientVersioningManager clientVersioningManager,
			PrivateGroupManager privateGroupManager,
			PrivateGroupFactory privateGroupFactory,
			GroupMessageFactory groupMessageFactory,
			IdentityManager identityManager, MessageParser messageParser,
			MessageEncoder messageEncoder, MessageTracker messageTracker,
			Clock clock) {
		this.db = db;
		this.clientHelper = clientHelper;
		this.clientVersioningManager = clientVersioningManager;
		this.privateGroupManager = privateGroupManager;
		this.privateGroupFactory = privateGroupFactory;
		this.groupMessageFactory = groupMessageFactory;
		this.identityManager = identityManager;
		this.messageParser = messageParser;
		this.messageEncoder = messageEncoder;
		this.messageTracker = messageTracker;
		this.clock = clock;
	}

	boolean isSubscribedPrivateGroup(Transaction txn, GroupId g)
			throws DbException {
		if (!db.containsGroup(txn, g)) return false;
		Group group = db.getGroup(txn, g);
		return group.getClientId().equals(PrivateGroupManager.CLIENT_ID);
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	boolean isValidDependency(S session, @Nullable MessageId dependency) {
		MessageId expected = session.getLastRemoteMessageId();
		if (dependency == null) return expected == null;
		return dependency.equals(expected);
	}

	void setPrivateGroupVisibility(Transaction txn, S session,
			Visibility preferred) throws DbException, FormatException {
		// Apply min of preferred visibility and client's visibility
		ContactId contactId =
				clientHelper.getContactId(txn, session.getContactGroupId());
		Visibility client = clientVersioningManager.getClientVisibility(txn,
				contactId, PrivateGroupManager.CLIENT_ID,
				PrivateGroupManager.MAJOR_VERSION);
		Visibility min = Visibility.min(preferred, client);
		db.setGroupVisibility(txn, contactId, session.getPrivateGroupId(), min);
	}

	Message sendInviteMessage(Transaction txn, S s,
			@Nullable String text, long timestamp, byte[] signature)
			throws DbException {
		Group g = db.getGroup(txn, s.getPrivateGroupId());
		PrivateGroup privateGroup;
		try {
			privateGroup = privateGroupFactory.parsePrivateGroup(g);
		} catch (FormatException e) {
			throw new DbException(e); // Invalid group descriptor
		}
		Message m;
		if (contactSupportsAutoDeletion(txn, s.getContactGroupId())) {
			// TODO: Look up the current auto-delete timer
			long timer = NO_AUTO_DELETE_TIMER;
			m = messageEncoder.encodeInviteMessage(s.getContactGroupId(),
					privateGroup.getId(), timestamp, privateGroup.getName(),
					privateGroup.getCreator(), privateGroup.getSalt(), text,
					signature, timer);
			sendMessage(txn, m, INVITE, privateGroup.getId(), true, timer);
		} else {
			m = messageEncoder.encodeInviteMessage(s.getContactGroupId(),
					privateGroup.getId(), timestamp, privateGroup.getName(),
					privateGroup.getCreator(), privateGroup.getSalt(), text,
					signature);
			sendMessage(txn, m, INVITE, privateGroup.getId(), true,
					NO_AUTO_DELETE_TIMER);
		}
		return m;
	}

	Message sendJoinMessage(Transaction txn, S s, boolean visibleInUi)
			throws DbException {
		Message m;
		if (contactSupportsAutoDeletion(txn, s.getContactGroupId())) {
			// TODO: Look up the current auto-delete timer if the message is
			//   visible in the UI (accepting an invitation)
			long timer = NO_AUTO_DELETE_TIMER;
			m = messageEncoder.encodeJoinMessage(s.getContactGroupId(),
					s.getPrivateGroupId(), getLocalTimestamp(s),
					s.getLastLocalMessageId(), timer);
			sendMessage(txn, m, JOIN, s.getPrivateGroupId(), visibleInUi,
					timer);
		} else {
			m = messageEncoder.encodeJoinMessage(s.getContactGroupId(),
					s.getPrivateGroupId(), getLocalTimestamp(s),
					s.getLastLocalMessageId());
			sendMessage(txn, m, JOIN, s.getPrivateGroupId(), visibleInUi,
					NO_AUTO_DELETE_TIMER);
		}
		return m;
	}

	Message sendLeaveMessage(Transaction txn, S s, boolean visibleInUi)
			throws DbException {
		Message m;
		if (contactSupportsAutoDeletion(txn, s.getContactGroupId())) {
			// TODO: Look up the current auto-delete timer if the message is
			//   visible in the UI (declining an invitation)
			long timer = NO_AUTO_DELETE_TIMER;
			m = messageEncoder.encodeLeaveMessage(s.getContactGroupId(),
					s.getPrivateGroupId(), getLocalTimestamp(s),
					s.getLastLocalMessageId(), timer);
			sendMessage(txn, m, LEAVE, s.getPrivateGroupId(), visibleInUi,
					timer);
		} else {
			m = messageEncoder.encodeLeaveMessage(s.getContactGroupId(),
					s.getPrivateGroupId(), getLocalTimestamp(s),
					s.getLastLocalMessageId());
			sendMessage(txn, m, LEAVE, s.getPrivateGroupId(), visibleInUi,
					NO_AUTO_DELETE_TIMER);
		}
		return m;
	}

	Message sendAbortMessage(Transaction txn, S session) throws DbException {
		Message m = messageEncoder.encodeAbortMessage(
				session.getContactGroupId(), session.getPrivateGroupId(),
				getLocalTimestamp(session));
		sendMessage(txn, m, ABORT, session.getPrivateGroupId(), false,
				NO_AUTO_DELETE_TIMER);
		return m;
	}

	void markMessageVisibleInUi(Transaction txn, MessageId m)
			throws DbException {
		BdfDictionary meta = new BdfDictionary();
		messageEncoder.setVisibleInUi(meta, true);
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

	void markInviteAccepted(Transaction txn, MessageId m) throws DbException {
		BdfDictionary meta = new BdfDictionary();
		messageEncoder.setInvitationAccepted(meta, true);
		try {
			clientHelper.mergeMessageMetadata(txn, m, meta);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
	}

	void subscribeToPrivateGroup(Transaction txn, MessageId inviteId)
			throws DbException, FormatException {
		InviteMessage invite = messageParser.getInviteMessage(txn, inviteId);
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

	private void sendMessage(Transaction txn, Message m, MessageType type,
			GroupId privateGroupId, boolean visibleInConversation,
			long autoDeleteTimer) throws DbException {
		BdfDictionary meta = messageEncoder.encodeMetadata(type,
				privateGroupId, m.getTimestamp(), true, true,
				visibleInConversation, false, false, autoDeleteTimer);
		try {
			clientHelper.addLocalMessage(txn, m, meta, true, false);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
	}

	boolean contactSupportsAutoDeletion(Transaction txn, GroupId contactGroupId)
			throws DbException {
		try {
			ContactId c = clientHelper.getContactId(txn, contactGroupId);
			int minorVersion = clientVersioningManager
					.getClientMinorVersion(txn, c,
							GroupInvitationManager.CLIENT_ID,
							GroupInvitationManager.MAJOR_VERSION);
			// Auto-delete was added in client version 0.1
			return minorVersion >= 1;
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}
}
