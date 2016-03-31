package org.briarproject.api.reporting;

public interface DevConfig {

	byte[] getDevPublicKey();

	String getDevOnionAddress();

	int getDevReportPort();
}
