package net.sf.briar.plugins.file;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

class FileListener {

	private static final Logger LOG =
		Logger.getLogger(FileListener.class.getName());

	private final String filename;
	private final long end;
	private final CountDownLatch finished = new CountDownLatch(1);

	private volatile File file = null;

	FileListener(String filename, long timeout) {
		this.filename = filename;
		end = System.currentTimeMillis() + timeout;
	}

	File waitForFile() {
		long now = System.currentTimeMillis();
		try {
			finished.await(end - now, TimeUnit.MILLISECONDS);
		} catch(InterruptedException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
		}
		return file;
	}

	void addFile(File f) {
		if(filename.equals(f.getName())) {
			file = f;
			finished.countDown();
		}
	}
}
