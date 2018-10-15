package org.briarproject.briar.api.sharing;

import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.messaging.PrivateRequest;

import javax.annotation.Nullable;

public abstract class InvitationRequest<S extends Shareable> extends
		PrivateRequest<S> {

	private final boolean canBeOpened;

	public InvitationRequest(MessageId messageId, GroupId groupId, long time,
			boolean local, boolean sent, boolean seen, boolean read,
			SessionId sessionId, S object, @Nullable String text,
			boolean available, boolean canBeOpened) {
		super(messageId, groupId, time, local, sent, seen, read, sessionId,
				object, text, !available);
		this.canBeOpened = canBeOpened;
	}

	public boolean canBeOpened() {
		return canBeOpened;
	}
}
