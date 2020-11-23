package org.briarproject.briar.privategroup.invitation;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.privategroup.PrivateGroup;
import org.briarproject.briar.api.privategroup.PrivateGroupFactory;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.bramble.api.autodelete.AutoDeleteConstants.NO_AUTO_DELETE_TIMER;
import static org.briarproject.briar.client.MessageTrackerConstants.MSG_KEY_READ;
import static org.briarproject.briar.privategroup.invitation.GroupInvitationConstants.MSG_KEY_AUTO_DELETE_TIMER;
import static org.briarproject.briar.privategroup.invitation.GroupInvitationConstants.MSG_KEY_AVAILABLE_TO_ANSWER;
import static org.briarproject.briar.privategroup.invitation.GroupInvitationConstants.MSG_KEY_INVITATION_ACCEPTED;
import static org.briarproject.briar.privategroup.invitation.GroupInvitationConstants.MSG_KEY_LOCAL;
import static org.briarproject.briar.privategroup.invitation.GroupInvitationConstants.MSG_KEY_MESSAGE_TYPE;
import static org.briarproject.briar.privategroup.invitation.GroupInvitationConstants.MSG_KEY_PRIVATE_GROUP_ID;
import static org.briarproject.briar.privategroup.invitation.GroupInvitationConstants.MSG_KEY_TIMESTAMP;
import static org.briarproject.briar.privategroup.invitation.GroupInvitationConstants.MSG_KEY_VISIBLE_IN_UI;
import static org.briarproject.briar.privategroup.invitation.MessageType.INVITE;

@Immutable
@NotNullByDefault
class MessageParserImpl implements MessageParser {

	private final PrivateGroupFactory privateGroupFactory;
	private final ClientHelper clientHelper;

	@Inject
	MessageParserImpl(PrivateGroupFactory privateGroupFactory,
			ClientHelper clientHelper) {
		this.privateGroupFactory = privateGroupFactory;
		this.clientHelper = clientHelper;
	}

	@Override
	public BdfDictionary getMessagesVisibleInUiQuery() {
		return BdfDictionary.of(new BdfEntry(MSG_KEY_VISIBLE_IN_UI, true));
	}

	@Override
	public BdfDictionary getInvitesAvailableToAnswerQuery() {
		return BdfDictionary.of(
				new BdfEntry(MSG_KEY_AVAILABLE_TO_ANSWER, true),
				new BdfEntry(MSG_KEY_MESSAGE_TYPE, INVITE.getValue())
		);
	}

	@Override
	public BdfDictionary getInvitesAvailableToAnswerQuery(
			GroupId privateGroupId) {
		return BdfDictionary.of(
				new BdfEntry(MSG_KEY_AVAILABLE_TO_ANSWER, true),
				new BdfEntry(MSG_KEY_MESSAGE_TYPE, INVITE.getValue()),
				new BdfEntry(MSG_KEY_PRIVATE_GROUP_ID, privateGroupId)
		);
	}

	@Override
	public MessageMetadata parseMetadata(BdfDictionary meta)
			throws FormatException {
		MessageType type = MessageType.fromValue(
				meta.getLong(MSG_KEY_MESSAGE_TYPE).intValue());
		GroupId privateGroupId =
				new GroupId(meta.getRaw(MSG_KEY_PRIVATE_GROUP_ID));
		long timestamp = meta.getLong(MSG_KEY_TIMESTAMP);
		boolean local = meta.getBoolean(MSG_KEY_LOCAL);
		boolean read = meta.getBoolean(MSG_KEY_READ, false);
		boolean visible = meta.getBoolean(MSG_KEY_VISIBLE_IN_UI, false);
		boolean available = meta.getBoolean(MSG_KEY_AVAILABLE_TO_ANSWER, false);
		boolean accepted = meta.getBoolean(MSG_KEY_INVITATION_ACCEPTED, false);
		long timer = meta.getLong(MSG_KEY_AUTO_DELETE_TIMER,
				NO_AUTO_DELETE_TIMER);
		return new MessageMetadata(type, privateGroupId, timestamp, local, read,
				visible, available, accepted, timer);
	}

	@Override
	public InviteMessage getInviteMessage(Transaction txn, MessageId m)
			throws DbException, FormatException {
		Message message = clientHelper.getMessage(txn, m);
		BdfList body = clientHelper.toList(message);
		return parseInviteMessage(message, body);
	}

	@Override
	public InviteMessage parseInviteMessage(Message m, BdfList body)
			throws FormatException {
		// Client version 0.0: Message type, creator, group name, salt,
		// optional text, signature.
		// Client version 0.1: Message type, creator, group name, salt,
		// optional text, signature, optional auto-delete timer.
		BdfList creatorList = body.getList(1);
		String groupName = body.getString(2);
		byte[] salt = body.getRaw(3);
		String text = body.getOptionalString(4);
		byte[] signature = body.getRaw(5);
		long timer = NO_AUTO_DELETE_TIMER;
		if (body.size() == 7) timer = body.getLong(6, NO_AUTO_DELETE_TIMER);

		Author creator = clientHelper.parseAndValidateAuthor(creatorList);
		PrivateGroup privateGroup = privateGroupFactory.createPrivateGroup(
				groupName, creator, salt);
		return new InviteMessage(m.getId(), m.getGroupId(),
				privateGroup.getId(), m.getTimestamp(), groupName, creator,
				salt, text, signature, timer);
	}

	@Override
	public JoinMessage parseJoinMessage(Message m, BdfList body)
			throws FormatException {
		// Client version 0.0: Message type, private group ID, optional
		// previous message ID.
		// Client version 0.1: Message type, private group ID, optional
		// previous message ID, optional auto-delete timer.
		GroupId privateGroupId = new GroupId(body.getRaw(1));
		byte[] b = body.getOptionalRaw(2);
		MessageId previousMessageId = b == null ? null : new MessageId(b);
		long timer = NO_AUTO_DELETE_TIMER;
		if (body.size() == 4) timer = body.getLong(3, NO_AUTO_DELETE_TIMER);

		return new JoinMessage(m.getId(), m.getGroupId(), privateGroupId,
				m.getTimestamp(), previousMessageId, timer);
	}

	@Override
	public LeaveMessage parseLeaveMessage(Message m, BdfList body)
			throws FormatException {
		// Client version 0.0: Message type, private group ID, optional
		// previous message ID.
		// Client version 0.1: Message type, private group ID, optional
		// previous message ID, optional auto-delete timer.
		GroupId privateGroupId = new GroupId(body.getRaw(1));
		byte[] b = body.getOptionalRaw(2);
		MessageId previousMessageId = b == null ? null : new MessageId(b);
		long timer = NO_AUTO_DELETE_TIMER;
		if (body.size() == 4) timer = body.getLong(3, NO_AUTO_DELETE_TIMER);

		return new LeaveMessage(m.getId(), m.getGroupId(), privateGroupId,
				m.getTimestamp(), previousMessageId, timer);
	}

	@Override
	public AbortMessage parseAbortMessage(Message m, BdfList body)
			throws FormatException {
		GroupId privateGroupId = new GroupId(body.getRaw(1));
		return new AbortMessage(m.getId(), m.getGroupId(), privateGroupId,
				m.getTimestamp());
	}

}
