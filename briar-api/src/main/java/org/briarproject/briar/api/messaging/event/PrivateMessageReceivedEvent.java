package org.briarproject.briar.api.messaging.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.api.messaging.PrivateMessageHeader;

import javax.annotation.concurrent.Immutable;

/**
 * An event that is broadcast when a new private message is received.
 */
@Immutable
@NotNullByDefault
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
