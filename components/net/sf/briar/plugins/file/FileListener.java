package net.sf.briar.plugins.file;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

class FileListener {

	private static final Logger LOG =
		Logger.getLogger(FileListener.class.getName());

	private final String filename;
	private final long end;

	private File file = null; // Locking: this

	FileListener(String filename, long timeout) {
		this.filename = filename;
		end = System.currentTimeMillis() + timeout;
	}

	synchronized File waitForFile() {
		long now = System.currentTimeMillis();
		while(file == null && now < end) {
			try {
				wait(end - now);
			} catch(InterruptedException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			}
			now = System.currentTimeMillis();
		}
		return file;
	}

	synchronized void addFile(File f) {
		if(filename.equals(f.getName())) {
			file = f;
			notifyAll();
		}
	}
}
