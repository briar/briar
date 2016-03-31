package org.briarproject.api.reporting;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * A task for reporting back to the developers.
 */
public interface DevReporter {

	/**
	 * Store a crash report encrypted on-disk to be sent later.
	 *
	 * @param crashReportDir the directory where crash reports are stored.
	 * @param crashReport    the crash report in the form expected by the server.
	 * @throws FileNotFoundException if the report could not be written.
	 */
	void encryptCrashReportToFile(File crashReportDir, String crashReport)
			throws FileNotFoundException;

	/**
	 * Send crash reports previously stored on-disk.
	 *
	 * @param crashReportDir the directory where crash reports are stored.
	 * @param socksPort      the SOCKS port of a Tor client.
	 */
	void sendCrashReports(File crashReportDir, int socksPort);
}
