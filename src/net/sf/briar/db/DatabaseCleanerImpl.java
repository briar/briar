package net.sf.briar.db;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import java.util.TimerTask;
import java.util.logging.Logger;

import net.sf.briar.api.clock.Timer;
import net.sf.briar.api.db.DbClosedException;
import net.sf.briar.api.db.DbException;

import com.google.inject.Inject;

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
		timer.scheduleAtFixedRate(this, 0L, msBetweenSweeps);
	}

	public void stopCleaning() {
		timer.cancel();
	}

	public void run() {
		if(callback == null) throw new IllegalStateException();
		try {
			if(callback.shouldCheckFreeSpace()) {
				callback.checkFreeSpaceAndClean();
			}
		} catch(DbClosedException e) {
			if(LOG.isLoggable(INFO)) LOG.info("Database closed, exiting");
		} catch(DbException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
			throw new Error(e); // Kill the application
		} catch(RuntimeException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
			throw new Error(e); // Kill the application
		}
	}
}
