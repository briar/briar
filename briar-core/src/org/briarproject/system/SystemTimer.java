package org.briarproject.system;

import java.util.TimerTask;

import org.briarproject.api.system.Timer;

/** Default timer implementation. */
public class SystemTimer implements Timer {

	private final java.util.Timer timer = new java.util.Timer();

	public void cancel() {
		timer.cancel();
	}

	public int purge() {
		return timer.purge();
	}

	public void schedule(TimerTask task, long delay) {
		timer.schedule(task, delay);
	}

	public void schedule(TimerTask task, long delay, long period) {
		timer.schedule(task, delay, period);
	}

	public void scheduleAtFixedRate(TimerTask task, long delay, long period) {
		timer.scheduleAtFixedRate(task, delay, period);
	}
}
