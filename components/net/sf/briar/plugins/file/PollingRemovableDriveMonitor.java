package net.sf.briar.plugins.file;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

class PollingRemovableDriveMonitor implements RemovableDriveMonitor, Runnable {

	private static final Logger LOG =
		Logger.getLogger(PollingRemovableDriveMonitor.class.getName());

	private final RemovableDriveFinder finder;
	private final long pollingInterval;
	private final Object pollingLock = new Object();

	private volatile boolean running = false;
	private volatile Callback callback = null;
	private volatile IOException exception = null;

	public PollingRemovableDriveMonitor(RemovableDriveFinder finder,
			long pollingInterval) {
		this.finder = finder;
		this.pollingInterval = pollingInterval;
	}

	public synchronized void start(Callback callback) throws IOException {
		if(running) throw new IllegalStateException();
		running = true;
		this.callback = callback;
		new Thread(this).start();
	}

	public synchronized void stop() throws IOException {
		if(!running) throw new IllegalStateException();
		running = false;
		if(exception != null) {
			IOException e = exception;
			exception = null;
			throw e;
		}
		synchronized(pollingLock) {
			pollingLock.notifyAll();
		}
	}

	public void run() {
		try {
			List<File> drives = finder.findRemovableDrives();
			while(running) {
				synchronized(pollingLock) {
					try {
						pollingLock.wait(pollingInterval);
					} catch(InterruptedException e) {
						if(LOG.isLoggable(Level.WARNING))
							LOG.warning(e.getMessage());
					}
				}
				if(!running) return;
				List<File> newDrives = finder.findRemovableDrives();
				for(File f : newDrives) {
					if(!drives.contains(f)) callback.driveInserted(f);
				}
				drives = newDrives;
			}
		} catch(IOException e) {
			exception = e;
		}
	}
}
