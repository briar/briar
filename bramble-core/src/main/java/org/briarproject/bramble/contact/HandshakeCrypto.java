package org.briarproject.bramble.contact;

import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.nullsafety.NotNullByDefault;

import java.security.GeneralSecurityException;

@NotNullByDefault
interface HandshakeCrypto {

	KeyPair generateEphemeralKeyPair();

	/**
	 * Derives the master key from the given static and ephemeral keys using
	 * the deprecated v0.0 key derivation method.
	 * <p>
	 * TODO: Remove this after a reasonable migration period (added 2023-03-10).
	 *
	 * @param alice Whether the local peer is Alice
	 */
	@Deprecated
	SecretKey deriveMasterKey_0_0(PublicKey theirStaticPublicKey,
			PublicKey theirEphemeralPublicKey, KeyPair ourStaticKeyPair,
			KeyPair ourEphemeralKeyPair, boolean alice)
			throws GeneralSecurityException;

	/**
	 * Derives the master key from the given static and ephemeral keys using
	 * the v0.1 key derivation method.
	 *
	 * @param alice Whether the local peer is Alice
	 */
	SecretKey deriveMasterKey_0_1(PublicKey theirStaticPublicKey,
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
