package org.briarproject.sharing;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.sharing.Shareable;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

public interface InviteeSessionStateFactory<S extends Shareable, IS extends InviteeSessionState> {

	IS build(SessionId sessionId, MessageId storageId, GroupId groupId,
			InviteeSessionState.State state, ContactId contactId,
			GroupId shareableId, BdfDictionary d) throws FormatException;

	IS build(SessionId sessionId, MessageId storageId, GroupId groupId,
			InviteeSessionState.State state, ContactId contactId, S shareable,
			MessageId invitationId);
}
