package net.sf.briar.api.db;

import net.sf.briar.api.Author;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.messaging.MessageId;

public class PrivateMessageHeader extends MessageHeader {

	private final ContactId contactId;
	private final boolean incoming;

	public PrivateMessageHeader(MessageId id, MessageId parent, Author author,
			String contentType, long timestamp, boolean read,
			ContactId contactId, boolean incoming) {
		super(id, parent, author, contentType, timestamp, read);
		this.contactId = contactId;
		this.incoming = incoming;
	}

	/**
	 * Returns the ID of the contact who is the sender (if incoming) or
	 * recipient (if outgoing) of this message.
	 */
	public ContactId getContactId() {
		return contactId;
	}

	/** Returns true if this is an incoming message. */
	public boolean isIncoming() {
		return incoming;
	}
}
