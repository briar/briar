package org.briarproject.api.sharing;

import org.briarproject.api.clients.BaseMessageHeader;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.NotNull;

public abstract class InvitationMessage extends BaseMessageHeader {

	private final SessionId sessionId;
	private final ContactId contactId;

	public InvitationMessage(@NotNull MessageId id,
			@NotNull SessionId sessionId, @NotNull GroupId groupId,
			@NotNull ContactId contactId, long time, boolean local,
			boolean sent, boolean seen, boolean read) {

		super(id, groupId, time, local, read, sent, seen);
		this.sessionId = sessionId;
		this.contactId = contactId;
	}

	@NotNull
	public SessionId getSessionId() {
		return sessionId;
	}

	@NotNull
	public ContactId getContactId() {
		return contactId;
	}

}
