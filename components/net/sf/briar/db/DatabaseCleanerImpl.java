package net.sf.briar.db;

class DatabaseCleanerImpl implements DatabaseCleaner, Runnable {

	private final Object lock = new Object();
	private final Thread cleanerThread = new Thread(this);

	private volatile Callback callback;
	private volatile long msBetweenSweeps;
	private volatile boolean stopped = false; // Locking: lock

	public void startCleaning(Callback callback, long msBetweenSweeps) {
		this.callback = callback;
		this.msBetweenSweeps = msBetweenSweeps;
		cleanerThread.start();
	}

	public void stopCleaning() {
		// If the cleaner thread is waiting, wake it up
		synchronized(lock) {
			stopped = true;
			lock.notifyAll();
		}
		try {
			cleanerThread.join();
		} catch(InterruptedException ignored) {}
	}

	public void run() {
		try {
			while(true) {
				if(callback.shouldCheckFreeSpace()) {
					callback.checkFreeSpaceAndClean();
				} else {
					synchronized(lock) {
						if(stopped) break;
						try {
							lock.wait(msBetweenSweeps);
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
