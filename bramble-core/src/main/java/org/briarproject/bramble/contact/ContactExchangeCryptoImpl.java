package org.briarproject.bramble.contact;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.PrivateKey;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.security.GeneralSecurityException;

import javax.inject.Inject;

import static org.briarproject.bramble.contact.ContactExchangeConstants.ALICE_KEY_LABEL;
import static org.briarproject.bramble.contact.ContactExchangeConstants.ALICE_NONCE_LABEL;
import static org.briarproject.bramble.contact.ContactExchangeConstants.BOB_KEY_LABEL;
import static org.briarproject.bramble.contact.ContactExchangeConstants.BOB_NONCE_LABEL;
import static org.briarproject.bramble.contact.ContactExchangeConstants.PROTOCOL_VERSION;
import static org.briarproject.bramble.contact.ContactExchangeConstants.SIGNING_LABEL;

@NotNullByDefault
class ContactExchangeCryptoImpl implements ContactExchangeCrypto {

	private static final byte[] PROTOCOL_VERSION_BYTES =
			new byte[] {PROTOCOL_VERSION};

	private final CryptoComponent crypto;

	@Inject
	ContactExchangeCryptoImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	@Override
	public SecretKey deriveHeaderKey(SecretKey masterKey, boolean alice) {
		String label = alice ? ALICE_KEY_LABEL : BOB_KEY_LABEL;
		return crypto.deriveKey(label, masterKey, PROTOCOL_VERSION_BYTES);
	}

	@Override
	public byte[] sign(PrivateKey privateKey, SecretKey masterKey,
			boolean alice) {
		byte[] nonce = deriveNonce(masterKey, alice);
		try {
			return crypto.sign(SIGNING_LABEL, nonce, privateKey);
		} catch (GeneralSecurityException e) {
			throw new AssertionError();
		}
	}

	@Override
	public boolean verify(PublicKey publicKey,
			SecretKey masterKey, boolean alice, byte[] signature) {
		byte[] nonce = deriveNonce(masterKey, alice);
		try {
			return crypto.verifySignature(signature, SIGNING_LABEL, nonce,
					publicKey);
		} catch (GeneralSecurityException e) {
			return false;
		}
	}

	private byte[] deriveNonce(SecretKey masterKey, boolean alice) {
		String label = alice ? ALICE_NONCE_LABEL : BOB_NONCE_LABEL;
		return crypto.mac(label, masterKey, PROTOCOL_VERSION_BYTES);
	}
}
