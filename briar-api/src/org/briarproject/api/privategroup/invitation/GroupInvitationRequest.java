package org.briarproject.api.privategroup.invitation;

import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.identity.Author;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sharing.InvitationRequest;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;

@Immutable
@NotNullByDefault
public class GroupInvitationRequest extends InvitationRequest {

	private final String groupName;
	private final Author creator;

	public GroupInvitationRequest(MessageId id, SessionId sessionId,
			GroupId groupId, Author creator, ContactId contactId,
			String groupName, String message, boolean available, long time,
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
