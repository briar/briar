package net.sf.briar.plugins.file;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

class PollingRemovableDriveMonitor implements RemovableDriveMonitor, Runnable {

	private final RemovableDriveFinder finder;
	private final long pollingInterval;
	private final LinkedList<File> inserted;
	private final LinkedList<IOException> exceptions;
	private final Object pollingLock;

	private boolean started = false, stopped = false;
	private Thread pollingThread = null;

	public PollingRemovableDriveMonitor(RemovableDriveFinder finder,
			long pollingInterval) {
		this.finder = finder;
		this.pollingInterval = pollingInterval;
		inserted = new LinkedList<File>();
		exceptions = new LinkedList<IOException>();
		pollingLock = new Object();
	}

	public synchronized void start() throws IOException {
		if(started || stopped) throw new IllegalStateException();
		started = true;
		pollingThread = new Thread(this);
		pollingThread.start();
	}

	public synchronized File waitForInsertion() throws IOException {
		if(!started || stopped) throw new IllegalStateException();
		if(!exceptions.isEmpty()) throw exceptions.poll();
		while(inserted.isEmpty()) {
			try {
				wait();
			} catch(InterruptedException ignored) {}
			if(!exceptions.isEmpty()) throw exceptions.poll();
		}
		return inserted.poll();
	}

	public synchronized void stop() throws IOException {
		if(!started || stopped) throw new IllegalStateException();
		if(!exceptions.isEmpty()) throw exceptions.poll();
		stopped = true;
		synchronized(pollingLock) {
			pollingLock.notifyAll();
		}
	}

	public void run() {
		try {
			List<File> drives = finder.findRemovableDrives();
			while(true) {
				synchronized(this) {
					if(stopped) return;
				}
				synchronized(pollingLock) {
					try {
						pollingLock.wait(pollingInterval);
					} catch(InterruptedException ignored) {}
				}
				synchronized(this) {
					if(stopped) return;
				}
				List<File> newDrives = finder.findRemovableDrives();
				for(File f : newDrives) {
					if(!drives.contains(f)) {
						synchronized(this) {
							inserted.add(f);
							notifyAll();
						}
					}
				}
				drives = newDrives;
			}
		} catch(IOException e) {
			synchronized(this) {
				exceptions.add(e);
				notifyAll();
			}
		}
	}
}
