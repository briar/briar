package org.briarproject.bramble.api.reporting;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * A task for reporting back to the developers.
 */
@NotNullByDefault
public interface DevReporter {

	/**
	 * Stores an encrypted report on disk to be sent later.
	 *
	 * @param reportDir the directory where reports are stored.
	 * @param report the report in the form expected by the server.
	 * @throws FileNotFoundException if the report could not be written.
	 */
	void encryptReportToFile(File reportDir, String filename, String report)
			throws FileNotFoundException;

	/**
	 * Sends any reports previously stored on disk.
	 *
	 * @param reportDir the directory where reports are stored.
	 */
	void sendReports(File reportDir);
}
