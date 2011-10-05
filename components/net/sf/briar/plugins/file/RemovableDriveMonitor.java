package net.sf.briar.plugins.file;

import java.io.File;
import java.io.IOException;

interface RemovableDriveMonitor {

	void start() throws IOException;

	File waitForInsertion() throws IOException;

	void stop() throws IOException;
}
