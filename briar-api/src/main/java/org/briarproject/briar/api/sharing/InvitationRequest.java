package org.briarproject.briar.api.sharing;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class InvitationRequest<S extends Shareable> extends InvitationMessage {

	private final S shareable;
	@Nullable
	private final String message;
	private final boolean available, canBeOpened;

	public InvitationRequest(MessageId id, GroupId groupId, long time,
			boolean local, boolean sent, boolean seen, boolean read,
			SessionId sessionId, S shareable, ContactId contactId,
			@Nullable String message, boolean available, boolean canBeOpened) {
		super(id, groupId, time, local, sent, seen, read, sessionId, contactId);
		this.shareable = shareable;
		this.message = message;
		this.available = available;
		this.canBeOpened = canBeOpened;
	}

	@Nullable
	public String getMessage() {
		return message;
	}

	public boolean isAvailable() {
		return available;
	}

	public boolean canBeOpened() {
		return canBeOpened;
	}

	public S getShareable() {
		return shareable;
	}

}
