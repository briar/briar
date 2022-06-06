package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.File;
import java.io.IOException;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
@NotNullByDefault
interface MailboxFileManager {

	/**
	 * Creates an empty file for storing a download.
	 */
	File createTempFileForDownload() throws IOException;

	/**
	 * Handles a file that has been downloaded. The file should be created
	 * with {@link #createTempFileForDownload()}.
	 */
	void handleDownloadedFile(File f);
}
