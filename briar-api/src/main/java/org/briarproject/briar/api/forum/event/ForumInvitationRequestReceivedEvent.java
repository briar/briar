package org.briarproject.briar.api.forum.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.forum.Forum;
import org.briarproject.briar.api.sharing.InvitationRequest;
import org.briarproject.briar.api.sharing.event.InvitationRequestReceivedEvent;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class ForumInvitationRequestReceivedEvent extends
		InvitationRequestReceivedEvent<Forum> {

	public ForumInvitationRequestReceivedEvent(Forum forum, ContactId contactId,
			InvitationRequest<Forum> request) {
		super(forum, contactId, request);
	}

}
