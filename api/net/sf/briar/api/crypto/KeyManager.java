package net.sf.briar.api.crypto;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.transport.ConnectionContext;

public interface KeyManager {

	/**
	 * Returns a connection context for connecting to the given contact over
	 * the given transport, or null if the contact does not support the
	 * transport.
	 */
	ConnectionContext getConnectionContext(ContactId c, TransportId t);
}
