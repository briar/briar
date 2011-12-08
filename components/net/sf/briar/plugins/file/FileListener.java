package net.sf.briar.plugins.file;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class FileListener {

	private final String filename;
	private final long end;
	private final CountDownLatch finished = new CountDownLatch(1);

	private volatile File file = null;

	FileListener(String filename, long timeout) {
		this.filename = filename;
		end = System.currentTimeMillis() + timeout;
	}

	File waitForFile() throws InterruptedException {
		finished.await(end - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
		return file;
	}

	void addFile(File f) {
		if(filename.equals(f.getName())) {
			file = f;
			finished.countDown();
		}
	}
}
