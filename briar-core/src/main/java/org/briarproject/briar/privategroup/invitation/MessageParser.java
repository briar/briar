package org.briarproject.briar.privategroup.invitation;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;

@NotNullByDefault
interface MessageParser {

	BdfDictionary getMessagesVisibleInUiQuery();

	BdfDictionary getInvitesAvailableToAnswerQuery();

	BdfDictionary getInvitesAvailableToAnswerQuery(GroupId privateGroupId);

	MessageMetadata parseMetadata(BdfDictionary meta) throws FormatException;

	InviteMessage parseInviteMessage(Message m, BdfList body)
			throws FormatException;

	JoinMessage parseJoinMessage(Message m, BdfList body)
			throws FormatException;

	LeaveMessage parseLeaveMessage(Message m, BdfList body)
			throws FormatException;

	AbortMessage parseAbortMessage(Message m, BdfList body)
			throws FormatException;

}
