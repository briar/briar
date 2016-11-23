package org.briarproject.bramble.api.reporting;

import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface DevConfig {

	PublicKey getDevPublicKey();

	String getDevOnionAddress();
}
