package org.briarproject.sharing;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.sharing.Shareable;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

public interface SharerSessionStateFactory<S extends Shareable, SS extends SharerSessionState> {

	SS build(SessionId sessionId, MessageId storageId, GroupId groupId,
			SharerSessionState.State state, ContactId contactId,
			GroupId shareableId, BdfDictionary d) throws FormatException;

	SS build(SessionId sessionId, MessageId storageId, GroupId groupId,
			SharerSessionState.State state, ContactId contactId, S shareable);
}
