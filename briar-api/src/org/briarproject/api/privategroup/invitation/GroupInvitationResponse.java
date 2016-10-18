package org.briarproject.api.privategroup.invitation;

import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.identity.Author;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sharing.InvitationResponse;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.NotNull;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;

@Immutable
@NotNullByDefault
public class GroupInvitationResponse extends InvitationResponse {

	private final String groupName;
	private final Author creator;

	public GroupInvitationResponse(MessageId id, SessionId sessionId,
			GroupId groupId, String groupName, Author creator,
			ContactId contactId, boolean accept, long time, boolean local,
			boolean sent, boolean seen, boolean read) {
		super(id, sessionId, groupId, contactId, accept, time, local, sent,
				seen, read);
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
