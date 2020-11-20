package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;

import javax.annotation.Nullable;

@NotNullByDefault
interface MessageEncoder {

	BdfDictionary encodeMetadata(MessageType type, GroupId shareableId,
			long timestamp, boolean local, boolean read, boolean visible,
			boolean available, boolean accepted, long autoDeleteTimer);

	void setVisibleInUi(BdfDictionary meta, boolean visible);

	void setAvailableToAnswer(BdfDictionary meta, boolean available);

	void setInvitationAccepted(BdfDictionary meta, boolean accepted);

	/**
	 * Encodes an invite message without an auto-delete timer.
	 */
	Message encodeInviteMessage(GroupId contactGroupId, long timestamp,
			@Nullable MessageId previousMessageId, BdfList descriptor,
			@Nullable String text);

	/**
	 * Encodes an invite message with an optional auto-delete timer. This
	 * requires the contact to support client version 0.1 or higher.
	 */
	Message encodeInviteMessage(GroupId contactGroupId, long timestamp,
			@Nullable MessageId previousMessageId, BdfList descriptor,
			@Nullable String text, long autoDeleteTimer);

	/**
	 * Encodes an accept message without an auto-delete timer.
	 */
	Message encodeAcceptMessage(GroupId contactGroupId, GroupId shareableId,
			long timestamp, @Nullable MessageId previousMessageId);

	/**
	 * Encodes an accept message with an optional auto-delete timer. This
	 * requires the contact to support client version 0.1 or higher.
	 */
	Message encodeAcceptMessage(GroupId contactGroupId, GroupId shareableId,
			long timestamp, @Nullable MessageId previousMessageId,
			long autoDeleteTimer);

	/**
	 * Encodes a decline message without an auto-delete timer.
	 */
	Message encodeDeclineMessage(GroupId contactGroupId, GroupId shareableId,
			long timestamp, @Nullable MessageId previousMessageId);

	/**
	 * Encodes a decline message with an optional auto-delete timer. This
	 * requires the contact to support client version 0.1 or higher.
	 */
	Message encodeDeclineMessage(GroupId contactGroupId, GroupId shareableId,
			long timestamp, @Nullable MessageId previousMessageId,
			long autoDeleteTimer);

	Message encodeLeaveMessage(GroupId contactGroupId, GroupId shareableId,
			long timestamp, @Nullable MessageId previousMessageId);

	Message encodeAbortMessage(GroupId contactGroupId, GroupId shareableId,
			long timestamp, @Nullable MessageId previousMessageId);

}
