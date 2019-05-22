package org.briarproject.bramble.rendezvous;

import org.briarproject.bramble.api.Bytes;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.rendezvous.KeyMaterialSource;
import org.briarproject.bramble.api.rendezvous.RendezvousCrypto;

import java.security.GeneralSecurityException;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.bramble.api.rendezvous.RendezvousConstants.KEY_MATERIAL_LABEL;
import static org.briarproject.bramble.api.rendezvous.RendezvousConstants.RENDEZVOUS_KEY_LABEL;
import static org.briarproject.bramble.api.rendezvous.RendezvousConstants.PROTOCOL_VERSION;
import static org.briarproject.bramble.util.StringUtils.toUtf8;

@Immutable
@NotNullByDefault
class RendezvousCryptoImpl implements RendezvousCrypto {

	private final CryptoComponent crypto;

	@Inject
	RendezvousCryptoImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	@Override
	public SecretKey deriveRendezvousKey(PublicKey theirPublicKey,
			KeyPair ourKeyPair) throws GeneralSecurityException {
		byte[] ourPublicKeyBytes = ourKeyPair.getPublic().getEncoded();
		byte[] theirPublicKeyBytes = theirPublicKey.getEncoded();
		boolean alice = new Bytes(ourPublicKeyBytes).compareTo(
				new Bytes(theirPublicKeyBytes)) < 0;
		byte[][] inputs = {
				new byte[] {PROTOCOL_VERSION},
				alice ? ourPublicKeyBytes : theirPublicKeyBytes,
				alice ? theirPublicKeyBytes : ourPublicKeyBytes
		};
		return crypto.deriveSharedSecret(RENDEZVOUS_KEY_LABEL, theirPublicKey,
				ourKeyPair, inputs);
	}

	@Override
	public KeyMaterialSource createKeyMaterialSource(SecretKey rendezvousKey,
			TransportId t) {
		SecretKey sourceKey = crypto.deriveKey(KEY_MATERIAL_LABEL,
				rendezvousKey, toUtf8(t.getString()));
		return new KeyMaterialSourceImpl(sourceKey);
	}
}
