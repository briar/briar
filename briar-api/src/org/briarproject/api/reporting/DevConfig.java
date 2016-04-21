package org.briarproject.api.reporting;

import org.briarproject.api.crypto.PublicKey;

public interface DevConfig {

	PublicKey getDevPublicKey();

	String getDevOnionAddress();

	int getDevReportPort();
}
