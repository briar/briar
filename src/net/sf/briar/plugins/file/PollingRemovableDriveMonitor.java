package net.sf.briar.plugins.file;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.plugins.PluginExecutor;

class PollingRemovableDriveMonitor implements RemovableDriveMonitor, Runnable {

	private static final Logger LOG =
			Logger.getLogger(PollingRemovableDriveMonitor.class.getName());

	private final Executor pluginExecutor;
	private final RemovableDriveFinder finder;
	private final long pollingInterval;
	private final Object pollingLock = new Object();

	private volatile boolean running = false;
	private volatile Callback callback = null;

	public PollingRemovableDriveMonitor(@PluginExecutor Executor pluginExecutor,
			RemovableDriveFinder finder, long pollingInterval) {
		this.pluginExecutor = pluginExecutor;
		this.finder = finder;
		this.pollingInterval = pollingInterval;
	}

	public void start(Callback callback) throws IOException {
		synchronized(this) {
			assert !running;
			assert this.callback == null;
			running = true;
			this.callback = callback;
		}
		pluginExecutor.execute(this);
	}

	public void stop() throws IOException {
		synchronized(this) {
			assert running;
			assert callback != null;
			running = false;
			callback = null;
		}
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
			if(LOG.isLoggable(Level.INFO))
				LOG.info("Interrupted while waiting to poll");
			Thread.currentThread().interrupt();
		} catch(IOException e) {
			callback.exceptionThrown(e);
		}
	}
}
