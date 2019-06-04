package org.briarproject.bramble.contact;

import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.security.GeneralSecurityException;

@NotNullByDefault
interface HandshakeCrypto {

	KeyPair generateEphemeralKeyPair();

	/**
	 * Derives the master key from the given static and ephemeral keys.
	 *
	 * @param alice Whether the local peer is Alice
	 */
	SecretKey deriveMasterKey(PublicKey theirStaticPublicKey,
			PublicKey theirEphemeralPublicKey, KeyPair ourStaticKeyPair,
			KeyPair ourEphemeralKeyPair, boolean alice)
			throws GeneralSecurityException;

	/**
	 * Returns proof that the local peer knows the master key and therefore
	 * owns the static and ephemeral public keys sent by the local peer.
	 *
	 * @param alice Whether the proof is being created by Alice
	 */
	byte[] proveOwnership(SecretKey masterKey, boolean alice);

	/**
	 * Verifies the given proof that the remote peer knows the master key and
	 * therefore owns the static and ephemeral keys sent by the remote peer.
	 *
	 * @param alice Whether the proof was created by Alice
	 */
	boolean verifyOwnership(SecretKey masterKey, boolean alice, byte[] proof);
}
