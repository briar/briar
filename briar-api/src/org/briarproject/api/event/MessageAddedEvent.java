package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.sync.GroupId;

/** An event that is broadcast when a message is added to the database. */
public class MessageAddedEvent extends Event {

	private final GroupId groupId;
	private final ContactId contactId;

	public MessageAddedEvent(GroupId groupId, ContactId contactId) {
		this.groupId = groupId;
		this.contactId = contactId;
	}

	/** Returns the ID of the group to which the message belongs. */
	public GroupId getGroupId() {
		return groupId;
	}

	/**
	 * Returns the ID of the contact from which the message was received, or
	 * null if the message was locally generated.
	 */
	public ContactId getContactId() {
		return contactId;
	}
}
