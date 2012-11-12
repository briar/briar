package net.sf.briar.api.invitation;

/** An interface for monitoring the status of an invitation connection. */
public interface ConnectionCallback extends ConfirmationCallback {

	/**
	 * Called if the connection is successfully established.
	 * @param localCode the local confirmation code.
	 * @param remoteCode the remote confirmation code.
	 * @param c a callback to inform the remote peer of the result of the local
	 * peer's confirmation code comparison.
	 */
	void connectionEstablished(int localCode, int remoteCode,
			ConfirmationCallback c);

	/** Called if the connection cannot be established. */
	void connectionNotEstablished();
}
