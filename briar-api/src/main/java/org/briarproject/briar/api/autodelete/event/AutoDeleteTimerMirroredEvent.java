package org.briarproject.briar.api.autodelete.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class AutoDeleteTimerMirroredEvent extends Event {

	private final ContactId contactId;
	private final long newTimer;

	public AutoDeleteTimerMirroredEvent(ContactId contactId, long newTimer) {
		this.contactId = contactId;
		this.newTimer = newTimer;
	}

	public ContactId getContactId() {
		return contactId;
	}

	public long getNewTimer() {
		return newTimer;
	}
}
