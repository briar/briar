package org.briarproject.bramble.api.crypto;

import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.transport.TransportKeys;

public interface TransportCrypto {

	/**
	 * Derives initial transport keys for the given transport in the given
	 * rotation period from the given master secret.
	 * <p/>
	 * Used by the transport security protocol.
	 *
	 * @param alice whether the keys are for use by Alice or Bob.
	 */
	TransportKeys deriveTransportKeys(TransportId t, SecretKey master,
			long rotationPeriod, boolean alice);

	/**
	 * Rotates the given transport keys to the given rotation period. If the
	 * keys are for a future rotation period they are not rotated.
	 * <p/>
	 * Used by the transport security protocol.
	 */
	TransportKeys rotateTransportKeys(TransportKeys k, long rotationPeriod);

	/**
	 * Encodes the pseudo-random tag that is used to recognise a stream.
	 * <p/>
	 * Used by the transport security protocol.
	 */
	void encodeTag(byte[] tag, SecretKey tagKey, int protocolVersion,
			long streamNumber);
}
