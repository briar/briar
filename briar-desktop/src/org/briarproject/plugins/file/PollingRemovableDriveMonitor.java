package org.briarproject.plugins.file;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

class PollingRemovableDriveMonitor implements RemovableDriveMonitor, Runnable {

	private static final Logger LOG =
			Logger.getLogger(PollingRemovableDriveMonitor.class.getName());

	private final Executor ioExecutor;
	private final RemovableDriveFinder finder;
	private final int pollingInterval;
	private final Object pollingLock = new Object();

	private volatile boolean running = false;
	private volatile Callback callback = null;

	public PollingRemovableDriveMonitor(Executor ioExecutor,
			RemovableDriveFinder finder, int pollingInterval) {
		this.ioExecutor = ioExecutor;
		this.finder = finder;
		this.pollingInterval = pollingInterval;
	}

	public void start(Callback callback) throws IOException {
		this.callback = callback;
		running = true;
		ioExecutor.execute(this);
	}

	public void stop() throws IOException {
		running = false;
		synchronized(pollingLock) {
			pollingLock.notifyAll();
		}
	}

	public void run() {
		try {
			Collection<File> drives = finder.findRemovableDrives();
			while(running) {
				synchronized(pollingLock) {
					pollingLock.wait(pollingInterval);
				}
				if(!running) return;
				Collection<File> newDrives = finder.findRemovableDrives();
				for(File f : newDrives) {
					if(!drives.contains(f)) callback.driveInserted(f);
				}
				drives = newDrives;
			}
		} catch(InterruptedException e) {
			LOG.warning("Interrupted while waiting to poll");
			Thread.currentThread().interrupt();
		} catch(IOException e) {
			callback.exceptionThrown(e);
		}
	}
}
