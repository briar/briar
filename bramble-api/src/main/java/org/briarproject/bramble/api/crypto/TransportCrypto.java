package org.briarproject.bramble.api.crypto;

import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.transport.StaticTransportKeys;
import org.briarproject.bramble.api.transport.TransportKeys;

/**
 * Crypto operations for the transport security protocol - see
 * https://code.briarproject.org/briar/briar-spec/blob/master/protocols/BTP.md
 */
public interface TransportCrypto {

	/**
	 * Derives initial transport keys for the given transport in the given
	 * time period from the given master secret.
	 *
	 * @param alice whether the keys are for use by Alice or Bob.
	 * @param active whether the keys are usable for outgoing streams.
	 */
	TransportKeys deriveTransportKeys(TransportId t, SecretKey master,
			long timePeriod, boolean alice, boolean active);

	/**
	 * Rotates the given transport keys to the given time period. If the keys
	 * are for the given period or any later period they are not rotated.
	 */
	TransportKeys rotateTransportKeys(TransportKeys k, long timePeriod);

	/**
	 * Derives static transport keys for the given transport in the given time
	 * period from the given root key.
	 *
	 * @param alice whether the keys are for use by Alice or Bob.
	 */
	StaticTransportKeys deriveStaticTransportKeys(TransportId t,
			SecretKey rootKey, boolean alice, long timePeriod);

	/**
	 * Updates the given static transport keys to the given time period. If
	 * the keys are for the given period or any later period they are not
	 * updated.
	 */
	StaticTransportKeys updateTransportKeys(StaticTransportKeys k,
			long timePeriod);

	/**
	 * Encodes the pseudo-random tag that is used to recognise a stream.
	 */
	void encodeTag(byte[] tag, SecretKey tagKey, int protocolVersion,
			long streamNumber);
}
