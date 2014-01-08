package org.briarproject.plugins.file;

import java.io.File;
import java.io.IOException;

interface RemovableDriveMonitor {

	void start(Callback c) throws IOException;

	void stop() throws IOException;

	interface Callback {

		void driveInserted(File root);

		void exceptionThrown(IOException e);
	}
}
