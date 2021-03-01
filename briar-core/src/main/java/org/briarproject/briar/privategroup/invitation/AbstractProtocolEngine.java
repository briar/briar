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
import org.briarproject.briar.api.autodelete.AutoDeleteManager;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.conversation.ConversationManager;
import org.briarproject.briar.api.privategroup.GroupMessage;
import org.briarproject.briar.api.privategroup.GroupMessageFactory;
import org.briarproject.briar.api.privategroup.PrivateGroup;
import org.briarproject.briar.api.privategroup.PrivateGroupFactory;
import org.briarproject.briar.api.privategroup.PrivateGroupManager;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationManager;

import java.util.Collection;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import static java.lang.Math.max;
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
	private final AutoDeleteManager autoDeleteManager;
	private final ConversationManager conversationManager;
	private final Clock clock;

	AbstractProtocolEngine(
			DatabaseComponent db,
			ClientHelper clientHelper,
			ClientVersioningManager clientVersioningManager,
			PrivateGroupManager privateGroupManager,
			PrivateGroupFactory privateGroupFactory,
			GroupMessageFactory groupMessageFactory,
			IdentityManager identityManager,
			MessageParser messageParser,
			MessageEncoder messageEncoder,
			MessageTracker messageTracker,
			AutoDeleteManager autoDeleteManager,
			ConversationManager conversationManager,
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
		this.autoDeleteManager = autoDeleteManager;
		this.conversationManager = conversationManager;
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
			@Nullable String text, long timestamp, byte[] signature,
			long timer) throws DbException {
		Group g = db.getGroup(txn, s.getPrivateGroupId());
		PrivateGroup privateGroup;
		try {
			privateGroup = privateGroupFactory.parsePrivateGroup(g);
		} catch (FormatException e) {
			throw new DbException(e); // Invalid group descriptor
		}
		Message m;
		ContactId c = getContactId(txn, s.getContactGroupId());
		if (contactSupportsAutoDeletion(txn, c)) {
			m = messageEncoder.encodeInviteMessage(s.getContactGroupId(),
					privateGroup.getId(), timestamp, privateGroup.getName(),
					privateGroup.getCreator(), privateGroup.getSalt(), text,
					signature, timer);
			sendMessage(txn, m, INVITE, privateGroup.getId(), true, timer);
			// Set the auto-delete timer duration on the message
			if (timer != NO_AUTO_DELETE_TIMER) {
				db.setCleanupTimerDuration(txn, m.getId(), timer);
			}
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
		long localTimestamp = visibleInUi
				? getTimestampForVisibleMessage(txn, s)
				: getTimestampForInvisibleMessage(s);
		ContactId c = getContactId(txn, s.getContactGroupId());
		if (contactSupportsAutoDeletion(txn, c)) {
			// Set auto-delete timer if manually accepting an invitation
			long timer = NO_AUTO_DELETE_TIMER;
			if (visibleInUi) {
				timer = autoDeleteManager
						.getAutoDeleteTimer(txn, c, localTimestamp);
			}
			m = messageEncoder.encodeJoinMessage(s.getContactGroupId(),
					s.getPrivateGroupId(), localTimestamp,
					s.getLastLocalMessageId(), timer);
			sendMessage(txn, m, JOIN, s.getPrivateGroupId(), visibleInUi,
					timer);
			// Set the auto-delete timer duration on the message
			if (timer != NO_AUTO_DELETE_TIMER) {
				db.setCleanupTimerDuration(txn, m.getId(), timer);
			}
		} else {
			m = messageEncoder.encodeJoinMessage(s.getContactGroupId(),
					s.getPrivateGroupId(), localTimestamp,
					s.getLastLocalMessageId());
			sendMessage(txn, m, JOIN, s.getPrivateGroupId(), visibleInUi,
					NO_AUTO_DELETE_TIMER);
		}
		return m;
	}

	Message sendLeaveMessage(Transaction txn, S s) throws DbException {
		return sendLeaveMessage(txn, s, false, false);
	}

	Message sendLeaveMessage(Transaction txn, S s, boolean visibleInUi,
			boolean isAutoDecline) throws DbException {
		if (!visibleInUi && isAutoDecline) throw new IllegalArgumentException();
		Message m;
		long localTimestamp = visibleInUi
				? getTimestampForVisibleMessage(txn, s)
				: getTimestampForInvisibleMessage(s);
		ContactId c = getContactId(txn, s.getContactGroupId());
		if (contactSupportsAutoDeletion(txn, c)) {
			// Set auto-delete timer if declining an invitation
			long timer = NO_AUTO_DELETE_TIMER;
			if (visibleInUi) {
				timer = autoDeleteManager.getAutoDeleteTimer(txn, c,
						localTimestamp);
			}
			m = messageEncoder.encodeLeaveMessage(s.getContactGroupId(),
					s.getPrivateGroupId(), localTimestamp,
					s.getLastLocalMessageId(), timer);
			sendMessage(txn, m, LEAVE, s.getPrivateGroupId(), visibleInUi,
					timer, isAutoDecline);
			// Set the auto-delete timer duration on the local message
			if (timer != NO_AUTO_DELETE_TIMER) {
				db.setCleanupTimerDuration(txn, m.getId(), timer);
			}
		} else {
			m = messageEncoder.encodeLeaveMessage(s.getContactGroupId(),
					s.getPrivateGroupId(), localTimestamp,
					s.getLastLocalMessageId());
			sendMessage(txn, m, LEAVE, s.getPrivateGroupId(), visibleInUi,
					NO_AUTO_DELETE_TIMER);
		}
		return m;
	}

	Message sendAbortMessage(Transaction txn, S session) throws DbException {
		Message m = messageEncoder.encodeAbortMessage(
				session.getContactGroupId(), session.getPrivateGroupId(),
				getTimestampForInvisibleMessage(session));
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
		Collection<MessageId> results = clientHelper.getMessageIds(txn,
				session.getContactGroupId(), query);
		for (MessageId m : results)
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
				max(clock.currentTimeMillis(), invite.getTimestamp() + 1);
		// TODO: Create the join message on the crypto executor
		LocalAuthor member = identityManager.getLocalAuthor(txn);
		GroupMessage joinMessage = groupMessageFactory.createJoinMessage(
				privateGroup.getId(), timestamp, member, invite.getTimestamp(),
				invite.getSignature());
		privateGroupManager
				.addPrivateGroup(txn, privateGroup, joinMessage, false);
	}

	/**
	 * Returns a timestamp for a visible outgoing message. The timestamp is
	 * later than the timestamp of any message sent or received so far in the
	 * conversation, and later than the {@link #getSessionTimestamp(Session)
	 * session timestamp}.
	 */
	long getTimestampForVisibleMessage(Transaction txn, S s)
			throws DbException {
		ContactId c = getContactId(txn, s.getContactGroupId());
		long conversationTimestamp =
				conversationManager.getTimestampForOutgoingMessage(txn, c);
		return max(conversationTimestamp, getSessionTimestamp(s) + 1);
	}

	/**
	 * Returns a timestamp for an invisible outgoing message. The timestamp is
	 * later than the {@link #getSessionTimestamp(Session) session timestamp}.
	 */
	long getTimestampForInvisibleMessage(S s) {
		return max(clock.currentTimeMillis(), getSessionTimestamp(s) + 1);
	}

	/**
	 * Returns the latest timestamp of any message sent so far in the session,
	 * and any invite message sent or received so far in the session.
	 */
	private long getSessionTimestamp(S s) {
		return max(s.getLocalTimestamp(), s.getInviteTimestamp());
	}

	void receiveAutoDeleteTimer(Transaction txn,
			DeletableGroupInvitationMessage m) throws DbException {
		ContactId c = getContactId(txn, m.getContactGroupId());
		autoDeleteManager.receiveAutoDeleteTimer(txn, c, m.getAutoDeleteTimer(),
				m.getTimestamp());
	}

	private void sendMessage(Transaction txn, Message m, MessageType type,
			GroupId privateGroupId, boolean visibleInConversation,
			long autoDeleteTimer) throws DbException {
		sendMessage(txn, m, type, privateGroupId, visibleInConversation,
				autoDeleteTimer, false);
	}

	private void sendMessage(Transaction txn, Message m, MessageType type,
			GroupId privateGroupId, boolean visibleInConversation,
			long autoDeleteTimer, boolean isAutoDecline) throws DbException {
		BdfDictionary meta = messageEncoder.encodeMetadata(type,
				privateGroupId, m.getTimestamp(), true, true,
				visibleInConversation, false, false, autoDeleteTimer,
				isAutoDecline);
		try {
			clientHelper.addLocalMessage(txn, m, meta, true, false);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
	}

	private ContactId getContactId(Transaction txn, GroupId contactGroupId)
			throws DbException {
		try {
			return clientHelper.getContactId(txn, contactGroupId);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private boolean contactSupportsAutoDeletion(Transaction txn, ContactId c)
			throws DbException {
		int minorVersion = clientVersioningManager.getClientMinorVersion(txn, c,
				GroupInvitationManager.CLIENT_ID,
				GroupInvitationManager.MAJOR_VERSION);
		// Auto-delete was added in client version 0.1
		return minorVersion >= 1;
	}
}
