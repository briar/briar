package org.briarproject.bramble.api.reporting;

import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.File;

@NotNullByDefault
public interface DevConfig {

	/**
	 * Returns the public key for encrypting feedback and crash reports.
	 */
	PublicKey getDevPublicKey();

	/**
	 * Returns the onion address for submitting feedback and crash reports.
	 */
	String getDevOnionAddress();

	/**
	 * Returns the directory for storing unsent feedback and crash reports.
	 */
	File getReportDir();

	/**
	 * Returns the temporary file for passing the encrypted app log from the
	 * main process to the crash reporter process.
	 */
	File getTemporaryLogFile();
}
