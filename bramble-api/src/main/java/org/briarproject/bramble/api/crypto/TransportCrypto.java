package org.briarproject.bramble.api.crypto;

import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.transport.TransportKeys;

/**
 * Crypto operations for the transport security protocol - see
 * https://code.briarproject.org/akwizgran/briar-spec/blob/master/protocols/BTP.md
 */
public interface TransportCrypto {

	/**
	 * Derives initial transport keys for the given transport in the given
	 * rotation period from the given master secret.
	 *
	 * @param alice whether the keys are for use by Alice or Bob.
	 */
	TransportKeys deriveTransportKeys(TransportId t, SecretKey master,
			long rotationPeriod, boolean alice);

	/**
	 * Rotates the given transport keys to the given rotation period. If the
	 * keys are for the given period or any later period they are not rotated.
	 */
	TransportKeys rotateTransportKeys(TransportKeys k, long rotationPeriod);

	/**
	 * Encodes the pseudo-random tag that is used to recognise a stream.
	 */
	void encodeTag(byte[] tag, SecretKey tagKey, int protocolVersion,
			long streamNumber);
}
