package net.sf.briar.api.invitation;

/** A task for exchanging invitations with a remote peer. */
public interface InvitationTask {

	/** Returns the task's unique handle. */
	int getHandle();

	/**
	 * Adds a listener to be informed of state changes and returns the
	 * task's current state.
	 */
	InvitationState addListener(InvitationListener l);

	/** Removes the given listener. */
	void removeListener(InvitationListener l);

	/** Asynchronously starts the connection process. */
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
