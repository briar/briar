package net.sf.briar.api.transport;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.protocol.TransportId;

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

	void addWindow(ContactId c, TransportId t, long period, boolean alice,
			byte[] secret, long centre, byte[] bitmap) throws DbException;

	void removeWindow(ContactId c, TransportId t, long period);

	void removeWindows(ContactId c);
}
