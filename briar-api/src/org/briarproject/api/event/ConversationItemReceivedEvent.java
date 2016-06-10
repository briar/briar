package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.conversation.ConversationItem;

/**
 * An event that is broadcast when a new conversation item is received.
 */
public class ConversationItemReceivedEvent extends Event {

	private final ConversationItem item;
	private final ContactId contactId;

	public ConversationItemReceivedEvent(ConversationItem item,
			ContactId contactId) {
		this.item = item;
		this.contactId = contactId;
	}

	public ConversationItem getItem() {
		return item;
	}

	public ContactId getContactId() {
		return contactId;
	}
}
