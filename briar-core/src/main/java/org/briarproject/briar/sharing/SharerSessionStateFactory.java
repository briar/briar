package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.sharing.Shareable;

@NotNullByDefault
interface SharerSessionStateFactory<S extends Shareable, SS extends SharerSessionState> {

	SS build(SessionId sessionId, MessageId storageId, GroupId groupId,
			SharerSessionState.State state, ContactId contactId,
			GroupId shareableId, BdfDictionary d) throws FormatException;

	SS build(SessionId sessionId, MessageId storageId, GroupId groupId,
			SharerSessionState.State state, ContactId contactId, S shareable);
}
