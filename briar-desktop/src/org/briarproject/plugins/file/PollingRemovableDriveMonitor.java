package org.briarproject.plugins.file;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

class PollingRemovableDriveMonitor implements RemovableDriveMonitor, Runnable {

	private static final Logger LOG =
			Logger.getLogger(PollingRemovableDriveMonitor.class.getName());

	private final Executor ioExecutor;
	private final RemovableDriveFinder finder;
	private final long pollingInterval;

	private volatile boolean running = false;
	private volatile Callback callback = null;

	private final Lock synchLock = new ReentrantLock();
	private final Condition stopPolling = synchLock.newCondition();


	public PollingRemovableDriveMonitor(Executor ioExecutor,
			RemovableDriveFinder finder, long pollingInterval) {
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
		synchLock.lock();
		try {
			stopPolling.signalAll();
		} 
		finally {
			synchLock.unlock();
		}
	}

	public void run() {
		try {
			Collection<File> drives = finder.findRemovableDrives();
			while(running) {
				synchLock.lock();
				try {
					stopPolling.await(pollingInterval, TimeUnit.MILLISECONDS);
				} 
				finally{
					synchLock.unlock();
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
