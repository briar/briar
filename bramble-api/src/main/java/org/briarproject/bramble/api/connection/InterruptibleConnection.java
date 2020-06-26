package org.briarproject.bramble.api.connection;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

/**
 * A duplex sync connection that can be closed by interrupting its outgoing
 * sync session.
 */
@NotNullByDefault
public interface InterruptibleConnection {

	/**
	 * Interrupts the connection's outgoing sync session. If the underlying
	 * transport connection is alive and the remote peer is cooperative, this
	 * should result in both sync sessions ending and the connection being
	 * cleanly closed.
	 */
	void interruptOutgoingSession();
}
