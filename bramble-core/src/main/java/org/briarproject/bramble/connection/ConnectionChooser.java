package org.briarproject.bramble.connection;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.sync.Priority;

@NotNullByDefault
interface ConnectionChooser {

	/**
	 * Adds the given connection to the chooser with the given priority.
	 */
	void addConnection(ContactId c, TransportId t, DuplexSyncConnection conn,
			Priority p);

	/**
	 * Removes the given connection from the chooser.
	 */
	void removeConnection(ContactId c, TransportId t,
			DuplexSyncConnection conn);
}
