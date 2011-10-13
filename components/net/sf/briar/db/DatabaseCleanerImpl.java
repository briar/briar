package net.sf.briar.db;

import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.db.DbException;

class DatabaseCleanerImpl implements DatabaseCleaner, Runnable {

	private static final Logger LOG =
		Logger.getLogger(DatabaseCleanerImpl.class.getName());

	private Callback callback = null;
	private long msBetweenSweeps = 0L;
	private boolean stopped = false;

	public synchronized void startCleaning(Callback callback,
			long msBetweenSweeps) {
		this.callback = callback;
		this.msBetweenSweeps = msBetweenSweeps;
		new Thread(this).start();
	}

	public synchronized void stopCleaning() {
		stopped = true;
		notifyAll();
	}

	public void run() {
		while(true) {
			synchronized(this) {
				if(stopped) return;
				try {
					if(callback.shouldCheckFreeSpace()) {
						callback.checkFreeSpaceAndClean();
					} else {
						try {
							wait(msBetweenSweeps);
						} catch(InterruptedException ignored) {}
					}
				} catch(DbException e) {
					if(LOG.isLoggable(Level.WARNING))
						LOG.warning(e.getMessage());
				} catch(RuntimeException e) {
					if(LOG.isLoggable(Level.WARNING))
						LOG.warning(e.getMessage());
				}
			}
		}
	}
}
