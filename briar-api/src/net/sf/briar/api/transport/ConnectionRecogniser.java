package net.sf.briar.api.transport;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.messaging.TransportId;

/**
 * Maintains the connection reordering windows and decides whether incoming
 * connections should be accepted or rejected.
 */
public interface ConnectionRecogniser {

	/**
	 * Returns the context for the given connection if the connection was
	 * expected, or null if the connection was not expected.
	 */
	ConnectionContext acceptConnection(TransportId t, byte[] tag)
			throws DbException;

	void addSecret(TemporarySecret s);

	void removeSecret(ContactId c, TransportId t, long period);

	void removeSecrets(ContactId c);

	void removeSecrets(TransportId t);

	void removeSecrets();
}
