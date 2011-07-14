package net.sf.briar.db;

import java.util.concurrent.atomic.AtomicBoolean;

import com.google.inject.Inject;

class DatabaseCleanerImpl implements DatabaseCleaner, Runnable {

	private final Callback db;
	private final int msBetweenSweeps;
	private final AtomicBoolean stopped = new AtomicBoolean(false);
	private final Thread cleanerThread = new Thread(this);

	@Inject
	DatabaseCleanerImpl(Callback db, int msBetweenSweeps) {
		this.db = db;
		this.msBetweenSweeps = msBetweenSweeps;
	}

	public void startCleaning() {
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
				if(db.shouldCheckFreeSpace()) {
					db.checkFreeSpaceAndClean();
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
