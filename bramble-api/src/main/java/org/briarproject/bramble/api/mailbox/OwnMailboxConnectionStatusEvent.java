package org.briarproject.bramble.api.mailbox;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

/**
 * An event that is broadcast by {@link MailboxSettingsManager} when
 * recording the connection status of own Mailbox.
 */
@Immutable
@NotNullByDefault
public class OwnMailboxConnectionStatusEvent extends Event {

	private final MailboxStatus status;

	public OwnMailboxConnectionStatusEvent(MailboxStatus status) {
		this.status = status;
	}

	public MailboxStatus getStatus() {
		return status;
	}
}
