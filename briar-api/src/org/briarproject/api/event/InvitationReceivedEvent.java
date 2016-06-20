package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.forum.Forum;

public abstract class InvitationReceivedEvent extends Event {

	private final ContactId contactId;

	public InvitationReceivedEvent(ContactId contactId) {
		this.contactId = contactId;
	}

	public ContactId getContactId() {
		return contactId;
	}
}
