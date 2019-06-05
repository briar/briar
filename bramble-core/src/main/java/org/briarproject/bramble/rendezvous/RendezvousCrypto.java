package org.briarproject.bramble.rendezvous;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.rendezvous.KeyMaterialSource;

@NotNullByDefault
interface RendezvousCrypto {

	SecretKey deriveRendezvousKey(SecretKey staticMasterKey);

	KeyMaterialSource createKeyMaterialSource(SecretKey rendezvousKey,
			TransportId t);
}
