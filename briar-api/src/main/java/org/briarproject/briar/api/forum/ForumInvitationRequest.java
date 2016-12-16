package org.briarproject.briar.api.forum;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.sharing.InvitationRequest;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class ForumInvitationRequest extends InvitationRequest<Forum> {

	public ForumInvitationRequest(MessageId id, GroupId groupId, long time,
			boolean local, boolean sent, boolean seen, boolean read,
			SessionId sessionId, Forum forum, ContactId contactId,
			@Nullable String message, boolean available, boolean canBeOpened) {
		super(id, groupId, time, local, sent, seen, read, sessionId, forum,
				contactId, message, available, canBeOpened);
	}

	public String getForumName() {
		return getShareable().getName();
	}

}
