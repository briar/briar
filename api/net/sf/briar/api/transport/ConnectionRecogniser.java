package net.sf.briar.api.transport;

import net.sf.briar.api.db.DbException;
import net.sf.briar.api.protocol.TransportId;

/**
 * Maintains the connection reordering windows and decides whether incoming
 * connections should be accepted or rejected.
 */
public interface ConnectionRecogniser {

	/**
	 * Asynchronously calls one of the callback's connectionAccepted(),
	 * connectionRejected() or handleException() methods.
	 */
	void acceptConnection(TransportId t, byte[] encryptedIv,
			Callback c);

	interface Callback {

		void connectionAccepted(ConnectionContext ctx);

		void connectionRejected();

		void handleException(DbException e);
	}
}
