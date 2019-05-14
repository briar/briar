package org.briarproject.bramble.api.rendezvous;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;

@NotNullByDefault
public interface RendezvousCrypto {

	KeyMaterialSource createKeyMaterialSource(SecretKey masterKey,
			TransportId t);
}
