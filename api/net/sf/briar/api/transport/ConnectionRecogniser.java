package net.sf.briar.api.transport;

import net.sf.briar.api.db.DbException;
import net.sf.briar.api.protocol.TransportId;

/**
 * Maintains the connection reordering windows and decides whether incoming
 * connections should be accepted or rejected.
 */
public interface ConnectionRecogniser {

	/**
	 * Returns the connection's context if the connection should be accepted,
	 * or null if the connection should be rejected.
	 */
	ConnectionContext acceptConnection(TransportId t, byte[] encryptedIv)
	throws DbException;
}
