package net.sf.briar.api.setup;

import java.io.File;

/** A progress callback for the installation process. */
public interface SetupCallback {

	/** Returns true if the process has been cancelled by the user. */
	boolean isCancelled();

	void extractingFile(File f);

	void copyingFile(File f);

	void installed(File f);

	void error(String message);

	void notFound(File f);

	void notDirectory(File f);

	void notAllowed(File f);
}
