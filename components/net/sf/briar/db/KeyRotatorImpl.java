package net.sf.briar.db;

import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.db.DbException;

class KeyRotatorImpl extends TimerTask implements KeyRotator {

	private static final Logger LOG =
			Logger.getLogger(KeyRotatorImpl.class.getName());

	private volatile Callback callback = null;
	private volatile Timer timer = null;

	public void startRotating(Callback callback, long msBetweenRotations) {
		this.callback = callback;
		timer = new Timer();
		timer.scheduleAtFixedRate(this, 0L, msBetweenRotations);
	}

	public void stopRotating() {
		if(timer == null) throw new IllegalStateException();
		timer.cancel();
	}

	public void run() {
		if(callback == null) throw new IllegalStateException();
		try {
			callback.rotateKeys();
		} catch(DbException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
			throw new Error(e); // Kill the application
		} catch(RuntimeException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
			throw new Error(e); // Kill the application
		}
	}
}
