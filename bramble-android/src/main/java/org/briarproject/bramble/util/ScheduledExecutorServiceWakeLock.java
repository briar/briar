package org.briarproject.bramble.util;

import android.content.Context;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class ScheduledExecutorServiceWakeLock {

	final Context appContext;

	private static final ThreadFactory THREAD_FACTORY = new ThreadFactory() {
		@Override
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r);
			t.setDaemon(true);
			return t;
		}
	};

	private ScheduledExecutorService scheduledExecutorService = null; // Locking: this
	private Runnable runnable;

	public ScheduledExecutorServiceWakeLock(Context appContext) {
		this.appContext = appContext;
	}

	public void setRunnable(Runnable r){
		runnable = r;
	}

	public synchronized void setAlarm(long delay, TimeUnit unit) {
		if(runnable == null)
			return;
		if (scheduledExecutorService == null)
			scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(THREAD_FACTORY);
		scheduledExecutorService.schedule(runnable, delay, unit);
	}

	public synchronized void cancelAlarm() {
		if (scheduledExecutorService == null) throw new IllegalStateException();
		scheduledExecutorService.shutdownNow();
		scheduledExecutorService = null;
	}
}
