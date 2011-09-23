package net.sf.briar.db;

import java.util.concurrent.atomic.AtomicBoolean;

class DatabaseCleanerImpl implements DatabaseCleaner, Runnable {

	private final AtomicBoolean stopped = new AtomicBoolean(false);
	private final Thread cleanerThread = new Thread(this);

	private volatile Callback callback;
	private volatile long msBetweenSweeps;

	public void startCleaning(Callback callback, long msBetweenSweeps) {
		this.callback = callback;
		this.msBetweenSweeps = msBetweenSweeps;
		cleanerThread.start();
	}

	public void stopCleaning() {
		stopped.set(true);
		// If the cleaner thread is waiting, wake it up
		synchronized(stopped) {
			stopped.notifyAll();
		}
		try {
			cleanerThread.join();
		} catch(InterruptedException ignored) {}
	}

	public void run() {
		try {
			while(!stopped.get()) {
				if(callback.shouldCheckFreeSpace()) {
					callback.checkFreeSpaceAndClean();
				} else {
					synchronized(stopped) {
						try {
							stopped.wait(msBetweenSweeps);
						} catch(InterruptedException ignored) {}
					}
				}
			}
		} catch(Throwable t) {
			// FIXME: Work out what to do here
			t.printStackTrace();
			System.exit(1);
		}
	}
}
