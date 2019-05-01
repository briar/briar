package org.briarproject.bramble.api.crypto;

import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.transport.TransportKeys;

/**
 * Crypto operations for the transport security protocol - see
 * https://code.briarproject.org/briar/briar-spec/blob/master/protocols/BTP.md
 */
public interface TransportCrypto {

	/**
	 * Derives initial rotation mode transport keys for the given transport in
	 * the given time period from the given root key.
	 *
	 * @param alice Whether the keys are for use by Alice or Bob
	 * @param active Whether the keys are usable for outgoing streams
	 */
	TransportKeys deriveRotationKeys(TransportId t, SecretKey rootKey,
			long timePeriod, boolean alice, boolean active);

	/**
	 * Derives handshake keys for the given transport in the given time period
	 * from the given root key.
	 *
	 * @param alice Whether the keys are for use by Alice or Bob
	 */
	TransportKeys deriveHandshakeKeys(TransportId t, SecretKey rootKey,
			long timePeriod, boolean alice);

	/**
	 * Updates the given transport keys to the given time period. If the keys
	 * are for the given period or any later period they are not updated.
	 */
	TransportKeys updateTransportKeys(TransportKeys k, long timePeriod);

	/**
	 * Encodes the pseudo-random tag that is used to recognise a stream.
	 */
	void encodeTag(byte[] tag, SecretKey tagKey, int protocolVersion,
			long streamNumber);
}
