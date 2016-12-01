package org.briarproject.bramble.api.invitation;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

/**
 * A task for exchanging invitations with a remote peer.
 */
@NotNullByDefault
public interface InvitationTask {

	/**
	 * Adds a listener to be informed of state changes and returns the
	 * task's current state.
	 */
	InvitationState addListener(InvitationListener l);

	/**
	 * Removes the given listener.
	 */
	void removeListener(InvitationListener l);

	/**
	 * Asynchronously starts the connection process.
	 */
	void connect();

	/**
	 * Asynchronously informs the remote peer that the local peer's
	 * confirmation codes matched.
	 */
	void localConfirmationSucceeded();

	/**
	 * Asynchronously informs the remote peer that the local peer's
	 * confirmation codes did not match.
	 */
	void localConfirmationFailed();
}
