package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.sharing.Shareable;

interface InviteeSessionStateFactory<S extends Shareable, IS extends InviteeSessionState> {

	IS build(SessionId sessionId, MessageId storageId, GroupId groupId,
			InviteeSessionState.State state, ContactId contactId,
			GroupId shareableId, BdfDictionary d) throws FormatException;

	IS build(SessionId sessionId, MessageId storageId, GroupId groupId,
			InviteeSessionState.State state, ContactId contactId, S shareable,
			MessageId invitationId);
}
