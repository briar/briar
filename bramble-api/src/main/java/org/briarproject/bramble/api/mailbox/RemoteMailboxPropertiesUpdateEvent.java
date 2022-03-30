package org.briarproject.bramble.api.mailbox;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * An event that is broadcast when {@link MailboxPropertiesUpdate} are received
 * from a contact.
 */
@Immutable
@NotNullByDefault
public class RemoteMailboxPropertiesUpdateEvent extends Event {

	private final ContactId contactId;
	@Nullable
	private final MailboxPropertiesUpdate mailboxPropertiesUpdate;

	public RemoteMailboxPropertiesUpdateEvent(ContactId contactId,
			@Nullable MailboxPropertiesUpdate mailboxPropertiesUpdate) {
		this.contactId = contactId;
		this.mailboxPropertiesUpdate = mailboxPropertiesUpdate;
	}

	public ContactId getContact() {
		return contactId;
	}

	@Nullable
	public MailboxPropertiesUpdate getMailboxPropertiesUpdate() {
		return mailboxPropertiesUpdate;
	}
}
