package net.sf.briar.api.setup;

import java.io.File;

public interface SetupCallback {

	boolean isCancelled();

	void extractingFile(File f);

	void copyingFile(File f);

	void installed(File f);

	void error(String message);

	void notFound(File f);

	void notDirectory(File f);

	void notAllowed(File f);
}
