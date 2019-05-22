package org.briarproject.bramble.api.rendezvous;

import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;

import java.security.GeneralSecurityException;

@NotNullByDefault
public interface RendezvousCrypto {

	SecretKey deriveRendezvousKey(PublicKey theirPublicKey, KeyPair ourKeyPair)
			throws GeneralSecurityException;

	KeyMaterialSource createKeyMaterialSource(SecretKey rendezvousKey,
			TransportId t);
}
