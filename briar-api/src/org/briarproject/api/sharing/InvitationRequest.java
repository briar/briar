package org.briarproject.api.sharing;

import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class InvitationRequest extends InvitationMessage {

	private final String message;
	private final boolean available;

	public InvitationRequest(@NotNull MessageId id,
			@NotNull SessionId sessionId, @NotNull GroupId groupId,
			@NotNull ContactId contactId, @Nullable String message,
			boolean available, long time, boolean local, boolean sent,
			boolean seen, boolean read) {

		super(id, sessionId, groupId, contactId, time, local, sent, seen, read);
		this.message = message;
		this.available = available;
	}

	@Nullable
	public String getMessage() {
		return message;
	}

	public boolean isAvailable() {
		return available;
	}

}
