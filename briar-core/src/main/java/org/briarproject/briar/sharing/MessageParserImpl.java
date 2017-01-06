package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.sharing.Shareable;

import javax.annotation.concurrent.Immutable;

import static org.briarproject.briar.sharing.MessageType.INVITE;
import static org.briarproject.briar.sharing.SharingConstants.MSG_KEY_AVAILABLE_TO_ANSWER;
import static org.briarproject.briar.sharing.SharingConstants.MSG_KEY_LOCAL;
import static org.briarproject.briar.sharing.SharingConstants.MSG_KEY_MESSAGE_TYPE;
import static org.briarproject.briar.sharing.SharingConstants.MSG_KEY_READ;
import static org.briarproject.briar.sharing.SharingConstants.MSG_KEY_INVITATION_ACCEPTED;
import static org.briarproject.briar.sharing.SharingConstants.MSG_KEY_SHAREABLE_ID;
import static org.briarproject.briar.sharing.SharingConstants.MSG_KEY_TIMESTAMP;
import static org.briarproject.briar.sharing.SharingConstants.MSG_KEY_VISIBLE_IN_UI;

@Immutable
@NotNullByDefault
abstract class MessageParserImpl<S extends Shareable>
		implements MessageParser<S> {

	private final ClientHelper clientHelper;

	MessageParserImpl(ClientHelper clientHelper) {
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
	public BdfDictionary getInvitesAvailableToAnswerQuery(GroupId shareableId) {
		return BdfDictionary.of(
				new BdfEntry(MSG_KEY_AVAILABLE_TO_ANSWER, true),
				new BdfEntry(MSG_KEY_MESSAGE_TYPE, INVITE.getValue()),
				new BdfEntry(MSG_KEY_SHAREABLE_ID, shareableId)
		);
	}

	@Override
	public MessageMetadata parseMetadata(BdfDictionary meta)
			throws FormatException {
		MessageType type = MessageType
				.fromValue(meta.getLong(MSG_KEY_MESSAGE_TYPE).intValue());
		GroupId shareableId = new GroupId(meta.getRaw(MSG_KEY_SHAREABLE_ID));
		long timestamp = meta.getLong(MSG_KEY_TIMESTAMP);
		boolean local = meta.getBoolean(MSG_KEY_LOCAL);
		boolean read = meta.getBoolean(MSG_KEY_READ, false);
		boolean visible = meta.getBoolean(MSG_KEY_VISIBLE_IN_UI, false);
		boolean available = meta.getBoolean(MSG_KEY_AVAILABLE_TO_ANSWER, false);
		boolean accepted = meta.getBoolean(MSG_KEY_INVITATION_ACCEPTED, false);
		return new MessageMetadata(type, shareableId, timestamp, local, read,
				visible, available, accepted);
	}

	@Override
	public InviteMessage<S> getInviteMessage(Transaction txn, MessageId m)
			throws DbException, FormatException {
		Message message = clientHelper.getMessage(txn, m);
		if (message == null) throw new DbException();
		BdfList body = clientHelper.toList(message);
		return parseInviteMessage(message, body);
	}

	@Override
	public InviteMessage<S> parseInviteMessage(Message m, BdfList body)
			throws FormatException {
		byte[] b = body.getOptionalRaw(1);
		MessageId previousMessageId = (b == null ? null : new MessageId(b));
		BdfList descriptor = body.getList(2);
		S shareable = createShareable(descriptor);
		String message = body.getOptionalString(3);
		return new InviteMessage<S>(m.getId(), previousMessageId,
				m.getGroupId(), shareable, message, m.getTimestamp());
	}

	protected abstract S createShareable(BdfList descriptor)
			throws FormatException;

	@Override
	public AcceptMessage parseAcceptMessage(Message m, BdfList body)
			throws FormatException {
		GroupId shareableId = new GroupId(body.getRaw(1));
		byte[] b = body.getOptionalRaw(2);
		MessageId previousMessageId = (b == null ? null : new MessageId(b));
		return new AcceptMessage(m.getId(), previousMessageId, m.getGroupId(),
				shareableId, m.getTimestamp());
	}

	@Override
	public DeclineMessage parseDeclineMessage(Message m, BdfList body)
			throws FormatException {
		GroupId shareableId = new GroupId(body.getRaw(1));
		byte[] b = body.getOptionalRaw(2);
		MessageId previousMessageId = (b == null ? null : new MessageId(b));
		return new DeclineMessage(m.getId(), m.getGroupId(), shareableId,
				m.getTimestamp(), previousMessageId);
	}

	@Override
	public LeaveMessage parseLeaveMessage(Message m, BdfList body)
			throws FormatException {
		GroupId shareableId = new GroupId(body.getRaw(1));
		byte[] b = body.getOptionalRaw(2);
		MessageId previousMessageId = (b == null ? null : new MessageId(b));
		return new LeaveMessage(m.getId(), m.getGroupId(), shareableId,
				m.getTimestamp(), previousMessageId);
	}

	@Override
	public AbortMessage parseAbortMessage(Message m, BdfList body)
			throws FormatException {
		GroupId shareableId = new GroupId(body.getRaw(1));
		byte[] b = body.getOptionalRaw(2);
		MessageId previousMessageId = (b == null ? null : new MessageId(b));
		return new AbortMessage(m.getId(), m.getGroupId(), shareableId,
				m.getTimestamp(), previousMessageId);
	}

}
