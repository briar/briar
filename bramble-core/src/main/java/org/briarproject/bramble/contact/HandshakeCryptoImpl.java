package org.briarproject.bramble.contact;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.security.GeneralSecurityException;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.bramble.contact.HandshakeConstants.ALICE_PROOF_LABEL;
import static org.briarproject.bramble.contact.HandshakeConstants.BOB_PROOF_LABEL;
import static org.briarproject.bramble.contact.HandshakeConstants.MASTER_KEY_LABEL;

@Immutable
@NotNullByDefault
class HandshakeCryptoImpl implements HandshakeCrypto {

	private final CryptoComponent crypto;

	@Inject
	HandshakeCryptoImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	@Override
	public KeyPair generateEphemeralKeyPair() {
		return crypto.generateAgreementKeyPair();
	}

	@Override
	public SecretKey deriveMasterKey(PublicKey theirStaticPublicKey,
			PublicKey theirEphemeralPublicKey, KeyPair ourStaticKeyPair,
			KeyPair ourEphemeralKeyPair, boolean alice) throws
			GeneralSecurityException {
		byte[] theirStatic = theirStaticPublicKey.getEncoded();
		byte[] theirEphemeral = theirEphemeralPublicKey.getEncoded();
		byte[] ourStatic = ourStaticKeyPair.getPublic().getEncoded();
		byte[] ourEphemeral = ourEphemeralKeyPair.getPublic().getEncoded();
		byte[][] inputs = {
				alice ? ourStatic : theirStatic,
				alice ? theirStatic : ourStatic,
				alice ? ourEphemeral : theirEphemeral,
				alice ? theirEphemeral : ourEphemeral
		};
		return crypto.deriveSharedSecret(MASTER_KEY_LABEL, theirStaticPublicKey,
				theirEphemeralPublicKey, ourStaticKeyPair, ourEphemeralKeyPair,
				alice, inputs);
	}

	@Override
	public byte[] proveOwnership(SecretKey masterKey, boolean alice) {
		String label = alice ? ALICE_PROOF_LABEL : BOB_PROOF_LABEL;
		return crypto.mac(label, masterKey);
	}

	@Override
	public boolean verifyOwnership(SecretKey masterKey, boolean alice,
			byte[] proof) {
		String label = alice ? ALICE_PROOF_LABEL : BOB_PROOF_LABEL;
		return crypto.verifyMac(proof, label, masterKey);
	}
}
