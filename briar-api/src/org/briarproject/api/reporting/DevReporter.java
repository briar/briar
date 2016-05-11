package org.briarproject.api.reporting;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * A task for reporting back to the developers.
 */
public interface DevReporter {

	/**
	 * Store a report encrypted on-disk to be sent later.
	 *
	 * @param reportDir the directory where reports are stored.
	 * @param report    the report in the form expected by the server.
	 * @throws FileNotFoundException if the report could not be written.
	 */
	void encryptReportToFile(File reportDir, String filename,
			String report) throws FileNotFoundException;

	/**
	 * Send reports previously stored on-disk.
	 *
	 * @param reportDir the directory where reports are stored.
	 * @param socksPort the SOCKS port of a Tor client.
	 */
	void sendReports(File reportDir, int socksPort);
}
