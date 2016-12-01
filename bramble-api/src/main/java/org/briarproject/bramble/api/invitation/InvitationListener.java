package org.briarproject.bramble.api.invitation;

/**
 * An interface for receiving updates about the state of an
 * {@link InvitationTask}.
 */
public interface InvitationListener {

	/** Called if a connection to the remote peer is established. */
	void connectionSucceeded();

	/**
	 * Called if a connection to the remote peer cannot be established. This
	 * indicates that the protocol has ended unsuccessfully.
	 */
	void connectionFailed();

	/** Called if key agreement with the remote peer succeeds. */
	void keyAgreementSucceeded(int localCode, int remoteCode);

	/**
	 * Called if key agreement with the remote peer fails or the connection is
	 * lost. This indicates that the protocol has ended unsuccessfully.
	 */
	void keyAgreementFailed();

	/** Called if the remote peer's confirmation check succeeds. */
	void remoteConfirmationSucceeded();

	/**
	 * Called if remote peer's confirmation check fails or the connection is
	 * lost. This indicates that the protocol has ended unsuccessfully.
	 */
	void remoteConfirmationFailed();

	/**
	 * Called if the exchange of pseudonyms succeeds. This indicates that the
	 * protocol has ended successfully.
	 */
	void pseudonymExchangeSucceeded(String remoteName);

	/**
	 * Called if the exchange of pseudonyms fails or the connection is lost.
	 * This indicates that the protocol has ended unsuccessfully.
	 */
	void pseudonymExchangeFailed();
}
