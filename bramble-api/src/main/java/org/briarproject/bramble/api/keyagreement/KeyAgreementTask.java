package org.briarproject.bramble.api.keyagreement;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

/**
 * A task for conducting a key agreement with a remote peer.
 */
@NotNullByDefault
public interface KeyAgreementTask {

	/**
	 * Start listening for short-range BQP connections, if we are not already.
	 * <p/>
	 * Will trigger a KeyAgreementListeningEvent containing the local Payload,
	 * even if we are already listening.
	 */
	void listen();

	/**
	 * Stop listening for short-range BQP connections.
	 */
	void stopListening();

	/**
	 * Asynchronously start the connection process.
	 */
	void connectAndRunProtocol(Payload remotePayload);
}
