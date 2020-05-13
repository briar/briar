package org.briarproject.bramble.connection;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.sync.Priority;

/**
 * Chooses one connection per contact and transport to keep open and closes
 * any other connections.
 */
@NotNullByDefault
interface ConnectionChooser {

	/**
	 * Adds the given connection to the chooser with the given priority.
	 * <p>
	 * If the chooser has a connection with the same contact and transport and
	 * a lower {@link Priority priority}, that connection will be
	 * {@link InterruptibleConnection#interruptOutgoingSession() interrupted}.
	 * If the chooser has a connection with the same contact and transport and
	 * a higher priority, the newly added connection will be interrupted.
	 */
	void addConnection(ContactId c, TransportId t, InterruptibleConnection conn,
			Priority p);

	/**
	 * Removes the given connection from the chooser.
	 */
	void removeConnection(ContactId c, TransportId t,
			InterruptibleConnection conn);
}
