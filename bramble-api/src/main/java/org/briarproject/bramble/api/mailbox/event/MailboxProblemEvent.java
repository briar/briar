package org.briarproject.bramble.api.mailbox.event;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.mailbox.MailboxSettingsManager;
import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

/**
 * An event that is broadcast by {@link MailboxSettingsManager} when
 * recording a connection failure for own Mailbox
 * that has persistent for long enough for the mailbox owner to become active
 * and fix the problem with the mailbox.
 */
@Immutable
@NotNullByDefault
public class MailboxProblemEvent extends Event {

}
