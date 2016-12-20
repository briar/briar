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
public abstract class InvitationRequest extends InvitationMessage {

	@Nullable
	private final String message;
	private final boolean available, canBeOpened;

	public InvitationRequest(MessageId id, SessionId sessionId, GroupId groupId,
			ContactId contactId, @Nullable String message,
			GroupId invitedGroupId, boolean available,
			boolean canBeOpened, long time, boolean local, boolean sent,
			boolean seen, boolean read) {
		super(id, sessionId, groupId, contactId, invitedGroupId, time, local,
				sent, seen, read);
		if (available && canBeOpened) throw new IllegalArgumentException();
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

}
