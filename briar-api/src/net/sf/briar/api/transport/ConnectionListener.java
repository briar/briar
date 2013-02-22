package net.sf.briar.api.transport;

import net.sf.briar.api.ContactId;

/** An interface for listening for connection and disconnection events. */
public interface ConnectionListener {

	/** Called when a contact connects and has no existing connections. */
	void contactConnected(ContactId c);

	/** Called when a contact disconnects and has no remaining connections. */
	void contactDisconnected(ContactId c);
}
