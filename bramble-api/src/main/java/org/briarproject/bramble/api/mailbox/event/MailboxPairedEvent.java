package org.briarproject.bramble.api.mailbox.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.mailbox.MailboxUpdateWithMailbox;
import org.briarproject.nullsafety.NotNullByDefault;

import java.util.Map;

import javax.annotation.concurrent.Immutable;

/**
 * An event that is broadcast when a mailbox is paired.
 */
@Immutable
@NotNullByDefault
public class MailboxPairedEvent extends Event {

	private final MailboxProperties properties;
	private final Map<ContactId, MailboxUpdateWithMailbox> localUpdates;

	public MailboxPairedEvent(MailboxProperties properties,
			Map<ContactId, MailboxUpdateWithMailbox> localUpdates) {
		this.properties = properties;
		this.localUpdates = localUpdates;
	}

	public MailboxProperties getProperties() {
		return properties;
	}

	public Map<ContactId, MailboxUpdateWithMailbox> getLocalUpdates() {
		return localUpdates;
	}
}
