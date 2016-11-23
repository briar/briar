package org.briarproject.briar.api.forum.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.forum.Forum;
import org.briarproject.briar.api.forum.ForumInvitationRequest;
import org.briarproject.briar.api.sharing.event.InvitationRequestReceivedEvent;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class ForumInvitationRequestReceivedEvent extends
		InvitationRequestReceivedEvent<Forum> {

	public ForumInvitationRequestReceivedEvent(Forum forum, ContactId contactId,
			ForumInvitationRequest request) {
		super(forum, contactId, request);
	}

}
