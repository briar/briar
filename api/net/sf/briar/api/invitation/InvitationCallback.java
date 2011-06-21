package net.sf.briar.api.invitation;

import java.io.File;
import java.util.List;

public interface InvitationCallback {

	boolean isCancelled();

	void copyingFile(File f);

	void encryptingFile(File f);

	void created(List<File> files);

	void error(String message);

	void notFound(File f);

	void notDirectory(File f);

	void notAllowed(File f);
}
