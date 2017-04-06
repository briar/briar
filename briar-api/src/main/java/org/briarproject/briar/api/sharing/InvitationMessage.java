package org.briarproject.briar.api.sharing;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.BaseMessageHeader;
import org.briarproject.briar.api.client.SessionId;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class InvitationMessage extends BaseMessageHeader {

	private final SessionId sessionId;
	private final ContactId contactId;

	public InvitationMessage(MessageId id, GroupId groupId, long time,
			boolean local, boolean sent, boolean seen, boolean read,
			SessionId sessionId, ContactId contactId) {

		super(id, groupId, time, local, sent, seen, read);
		this.sessionId = sessionId;
		this.contactId = contactId;
	}

	public SessionId getSessionId() {
		return sessionId;
	}

	public ContactId getContactId() {
		return contactId;
	}

}
