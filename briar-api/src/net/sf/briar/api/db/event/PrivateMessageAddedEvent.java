package net.sf.briar.api.db.event;

import net.sf.briar.api.ContactId;

/**
 * An event that is broadcast when a private message is added to the database.
 */
public class PrivateMessageAddedEvent extends DatabaseEvent {

	private final ContactId contactId;
	private final boolean incoming;

	public PrivateMessageAddedEvent(ContactId contactId, boolean incoming) {
		this.contactId = contactId;
		this.incoming = incoming;
	}

	public ContactId getContactId() {
		return contactId;
	}

	public boolean isIncoming() {
		return incoming;
	}
}
