package net.sf.briar.api.invitation;

/**
 * Allows invitation connections to be established and their status to be
 * monitored.
 */
public interface InvitationManager {

	/**
	 * Tries to establish an invitation connection.
	 * @param localCode the local invitation code.
	 * @param remoteCode the remote invitation code.
	 * @param c1 a callback to be informed of the connection's status and the
	 * result of the remote peer's confirmation code comparison.
	 */
	void connect(int localCode, int remoteCode, ConnectionCallback c);
}
