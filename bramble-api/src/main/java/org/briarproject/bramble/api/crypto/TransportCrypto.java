package org.briarproject.bramble.api.crypto;

import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.transport.TransportKeys;

import java.security.GeneralSecurityException;

/**
 * Crypto operations for the transport security protocol - see
 * https://code.briarproject.org/briar/briar-spec/blob/master/protocols/BTP.md
 */
public interface TransportCrypto {

	/**
	 * Returns true if the local peer is Alice.
	 */
	boolean isAlice(PublicKey theirHandshakePublicKey,
			KeyPair ourHandshakeKeyPair);

	/**
	 * Derives the static master key shared with a contact or pending contact.
	 */
	SecretKey deriveStaticMasterKey(PublicKey theirHandshakePublicKey,
			KeyPair ourHandshakeKeyPair) throws GeneralSecurityException;

	/**
	 * Derives the handshake mode root key from the static master key. To
	 * prevent tag reuse, separate root keys are derived for contacts and
	 * pending contacts.
	 *
	 * @param pendingContact Whether the static master key is shared with a
	 * pending contact or a contact
	 */
	SecretKey deriveHandshakeRootKey(SecretKey staticMasterKey,
			boolean pendingContact);

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
