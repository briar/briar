package org.briarproject.db;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import java.util.TimerTask;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.briarproject.api.db.DbClosedException;
import org.briarproject.api.db.DbException;
import org.briarproject.api.system.Timer;

class DatabaseCleanerImpl extends TimerTask implements DatabaseCleaner {

	private static final Logger LOG =
			Logger.getLogger(DatabaseCleanerImpl.class.getName());

	private final Timer timer;

	private volatile Callback callback = null;

	@Inject
	DatabaseCleanerImpl(Timer timer) {
		this.timer = timer;
	}

	public void startCleaning(Callback callback, long msBetweenSweeps) {
		this.callback = callback;
		timer.scheduleAtFixedRate(this, 0, msBetweenSweeps);
	}

	public void stopCleaning() {
		timer.cancel();
	}

	public void run() {
		if(callback == null) throw new IllegalStateException();
		try {
			if(callback.shouldCheckFreeSpace()) {
				if(LOG.isLoggable(INFO)) LOG.info("Checking free space");
				callback.checkFreeSpaceAndClean();
			}
		} catch(DbClosedException e) {
			if(LOG.isLoggable(INFO)) LOG.info("Database closed, exiting");
		} catch(DbException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			throw new Error(e); // Kill the application
		} catch(RuntimeException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			throw new Error(e); // Kill the application
		}
	}
}
