package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.messaging.PrivateMessageHeader;
import org.briarproject.api.sync.GroupId;

/**
 * An event that is broadcast when a new private message was received.
 */
public class PrivateMessageReceivedEvent extends Event {

	private final PrivateMessageHeader messageHeader;
	private final ContactId contactId;
	private final GroupId groupId;

	public PrivateMessageReceivedEvent(PrivateMessageHeader messageHeader,
			ContactId contactId, GroupId groupId) {
		this.messageHeader = messageHeader;
		this.contactId = contactId;
		this.groupId = groupId;
	}

	public PrivateMessageHeader getMessageHeader() {
		return messageHeader;
	}

	public ContactId getContactId() {
		return contactId;
	}

	public GroupId getGroupId() {
		return groupId;
	}
}
