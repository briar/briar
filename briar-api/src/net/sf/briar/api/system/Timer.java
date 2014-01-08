package net.sf.briar.api.system;

import java.util.TimerTask;

/**
 * A wrapper around a {@link java.util.Timer} that allows it to be replaced for
 * testing.
 */
public interface Timer {

	/** @see {@link java.util.Timer#cancel()} */
	void cancel();

	/** @see {@link java.util.Timer#purge()} */
	int purge();

	/** @see {@link java.util.Timer#schedule(TimerTask, long)} */
	void schedule(TimerTask task, long delay);

	/** @see {@link java.util.Timer#schedule(TimerTask, long, long)} */
	void schedule(TimerTask task, long delay, long period);

	/**
	 * @see {@link java.util.Timer#scheduleAtFixedRate(TimerTask, long, long)}
	 */
	void scheduleAtFixedRate(TimerTask task, long delay, long period);
}
