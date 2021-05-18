package org.briarproject.briar.api.remotewipe;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import javax.annotation.concurrent.Immutable;

/**
 * An event which is activated when a critical amount of
 * remote wipe messages are received.
 */
@Immutable
@NotNullByDefault
public class RemoteWipeActivatedEvent extends Event {
}
