package net.sf.briar.api.crypto;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.transport.ConnectionContext;

public interface KeyManager {

	/**
	 * Starts the key manager and returns true if the manager started
	 * successfully. This method must be called after the database has been
	 * opened.
	 */
	boolean start();

	/** Stops the key manager. */
	void stop();

	/**
	 * Returns a connection context for connecting to the given contact over
	 * the given transport, or null if an error occurs or the contact does not
	 * support the transport.
	 */
	ConnectionContext getConnectionContext(ContactId c, TransportId t);
}
