package org.briarproject.bramble.api.mailbox.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.mailbox.MailboxUpdate;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

/**
 * An event that is broadcast when a mailbox update is sent to a contact.
 * <p>
 * Note that this event is not broadcast when a mailbox is paired or
 * unpaired, although updates are sent to all contacts in those situations.
 *
 * @see MailboxPairedEvent
 * @see MailboxUnpairedEvent
 */
@Immutable
@NotNullByDefault
public class MailboxUpdateSentEvent extends Event {

	private final ContactId contactId;
	private final MailboxUpdate mailboxUpdate;

	public MailboxUpdateSentEvent(ContactId contactId,
			MailboxUpdate mailboxUpdate) {
		this.contactId = contactId;
		this.mailboxUpdate = mailboxUpdate;
	}

	public ContactId getContactId() {
		return contactId;
	}

	public MailboxUpdate getMailboxUpdate() {
		return mailboxUpdate;
	}
}
