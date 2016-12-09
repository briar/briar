package org.briarproject.briar.api.sharing.event;


import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class ContactLeftShareableEvent extends Event {

	private final GroupId groupId;
	private final ContactId contactId;

	public ContactLeftShareableEvent(GroupId groupId, ContactId contactId) {
		this.groupId = groupId;
		this.contactId = contactId;
	}

	public GroupId getGroupId() {
		return groupId;
	}

	public ContactId getContactId() {
		return contactId;
	}

}
