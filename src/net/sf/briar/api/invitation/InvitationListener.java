package net.sf.briar.api.invitation;

/**
 * An interface for receiving updates about the state of an
 * {@link InvitationTask}.
 */
public interface InvitationListener {

	/** Called if a connection is established and key agreement succeeds. */
	void connectionSucceeded(int localCode, int remoteCode);

	/** Called if a connection cannot be established. */
	void connectionFailed();

	/**
	 * Informs the local peer that the remote peer's confirmation check
	 * succeeded.
	 */
	void remoteConfirmationSucceeded();

	/**
	 * Informs the local peer that the remote peer's confirmation check did
	 * not succeed, or the connection was lost during confirmation.
	 */
	void remoteConfirmationFailed();
}
