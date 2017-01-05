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
			boolean available, boolean accepted);

	void setVisibleInUi(BdfDictionary meta, boolean visible);

	void setAvailableToAnswer(BdfDictionary meta, boolean available);

	void setInvitationAccepted(BdfDictionary meta, boolean accepted);

	Message encodeInviteMessage(GroupId contactGroupId, long timestamp,
			@Nullable MessageId previousMessageId, BdfList descriptor,
			@Nullable String message);

	Message encodeAcceptMessage(GroupId contactGroupId, GroupId shareableId,
			long timestamp, @Nullable MessageId previousMessageId);

	Message encodeDeclineMessage(GroupId contactGroupId, GroupId shareableId,
			long timestamp, @Nullable MessageId previousMessageId);

	Message encodeLeaveMessage(GroupId contactGroupId, GroupId shareableId,
			long timestamp, @Nullable MessageId previousMessageId);

	Message encodeAbortMessage(GroupId contactGroupId, GroupId shareableId,
			long timestamp, @Nullable MessageId previousMessageId);

}
