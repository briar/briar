package org.briarproject.briar.privategroup.invitation;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.privategroup.PrivateGroup;
import org.briarproject.briar.api.privategroup.PrivateGroupFactory;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.briar.client.MessageTrackerConstants.MSG_KEY_READ;
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

	private final AuthorFactory authorFactory;
	private final PrivateGroupFactory privateGroupFactory;
	private final ClientHelper clientHelper;

	@Inject
	MessageParserImpl(AuthorFactory authorFactory,
			PrivateGroupFactory privateGroupFactory, ClientHelper clientHelper) {
		this.authorFactory = authorFactory;
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
		return new MessageMetadata(type, privateGroupId, timestamp, local, read,
				visible, available, accepted);
	}

	@Override
	public InviteMessage getInviteMessage(Transaction txn, MessageId m)
			throws DbException, FormatException {
		Message message = clientHelper.getMessage(txn, m);
		if (message == null) throw new DbException();
		BdfList body = clientHelper.toList(message);
		return parseInviteMessage(message, body);
	}

	@Override
	public InviteMessage parseInviteMessage(Message m, BdfList body)
			throws FormatException {
		String groupName = body.getString(1);
		String creatorName = body.getString(2);
		byte[] creatorPublicKey = body.getRaw(3);
		byte[] salt = body.getRaw(4);
		String message = body.getOptionalString(5);
		byte[] signature = body.getRaw(6);
		Author creator = authorFactory.createAuthor(creatorName,
				creatorPublicKey);
		PrivateGroup privateGroup = privateGroupFactory.createPrivateGroup(
				groupName, creator, salt);
		return new InviteMessage(m.getId(), m.getGroupId(),
				privateGroup.getId(), m.getTimestamp(), groupName, creator,
				salt, message, signature);
	}

	@Override
	public JoinMessage parseJoinMessage(Message m, BdfList body)
			throws FormatException {
		GroupId privateGroupId = new GroupId(body.getRaw(1));
		byte[] b = body.getOptionalRaw(2);
		MessageId previousMessageId = b == null ? null : new MessageId(b);
		return new JoinMessage(m.getId(), m.getGroupId(), privateGroupId,
				m.getTimestamp(), previousMessageId);
	}

	@Override
	public LeaveMessage parseLeaveMessage(Message m, BdfList body)
			throws FormatException {
		GroupId privateGroupId = new GroupId(body.getRaw(1));
		byte[] b = body.getOptionalRaw(2);
		MessageId previousMessageId = b == null ? null : new MessageId(b);
		return new LeaveMessage(m.getId(), m.getGroupId(), privateGroupId,
				m.getTimestamp(), previousMessageId);
	}

	@Override
	public AbortMessage parseAbortMessage(Message m, BdfList body)
			throws FormatException {
		GroupId privateGroupId = new GroupId(body.getRaw(1));
		return new AbortMessage(m.getId(), m.getGroupId(), privateGroupId,
				m.getTimestamp());
	}

}
