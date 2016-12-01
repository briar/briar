package org.briarproject.briar.api.privategroup.invitation;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.sharing.InvitationRequest;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class GroupInvitationRequest extends InvitationRequest {

	private final String groupName;
	private final Author creator;

	public GroupInvitationRequest(MessageId id, SessionId sessionId,
			GroupId groupId, ContactId contactId, @Nullable String message,
			String groupName, Author creator, boolean available, long time,
			boolean local, boolean sent, boolean seen, boolean read) {
		super(id, sessionId, groupId, contactId, message, available, time,
				local, sent, seen, read);
		this.groupName = groupName;
		this.creator = creator;
	}

	public String getGroupName() {
		return groupName;
	}

	public Author getCreator() {
		return creator;
	}

}
