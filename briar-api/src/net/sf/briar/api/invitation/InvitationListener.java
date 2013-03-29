package net.sf.briar.api.invitation;

/**
 * An interface for receiving updates about the state of an
 * {@link InvitationTask}.
 */
public interface InvitationListener {

	/** Called if a connection is established and key agreement succeeds. */
	void connectionSucceeded(int localCode, int remoteCode);

	/**
	 * Called if a connection cannot be established. This indicates that the
	 * protocol has ended unsuccessfully.
	 */
	void connectionFailed();

	/**
	 * Informs the local peer that the remote peer's confirmation check
	 * succeeded.
	 */
	void remoteConfirmationSucceeded();

	/**
	 * Informs the local peer that the remote peer's confirmation check did
	 * not succeed, or the connection was lost during confirmation. This
	 * indicates that the protocol has ended unsuccessfully.
	 */
	void remoteConfirmationFailed();

	/**
	 * Informs the local peer of the name used by the remote peer. Called if
	 * the exchange of pseudonyms succeeds. This indicates that the protocol
	 * has ended successfully.
	 */
	void pseudonymExchangeSucceeded(String remoteName);

	/**
	 * Called if the exchange of pseudonyms fails. This indicates that the
	 * protocol has ended unsuccessfully.
	 */
	void pseudonymExchangeFailed();
}
