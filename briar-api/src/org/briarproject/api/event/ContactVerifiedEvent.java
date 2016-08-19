package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;

/** An event that is broadcast when a contact is verified. */
public class ContactVerifiedEvent extends Event {

	private final ContactId contactId;

	public ContactVerifiedEvent(ContactId contactId) {
		this.contactId = contactId;
	}

	public ContactId getContactId() {
		return contactId;
	}

}
