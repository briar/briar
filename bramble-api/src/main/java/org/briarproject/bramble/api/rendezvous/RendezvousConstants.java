package org.briarproject.bramble.api.rendezvous;

import static java.util.concurrent.TimeUnit.DAYS;

public interface RendezvousConstants {

	/**
	 * The current version of the rendezvous protocol.
	 */
	byte PROTOCOL_VERSION = 0;

	/**
	 * How long to try to rendezvous with a pending contact before giving up.
	 */
	long RENDEZVOUS_TIMEOUT_MS = DAYS.toMillis(2);

	/**
	 * Label for deriving the rendezvous key from the handshake key pairs.
	 */
	String RENDEZVOUS_KEY_LABEL =
			"org.briarproject.bramble.rendezvous/RENDEZVOUS_KEY";

	/**
	 * Label for deriving key material from the rendezvous key.
	 */
	String KEY_MATERIAL_LABEL =
			"org.briarproject.bramble.rendezvous/KEY_MATERIAL";
}
