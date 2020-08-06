package org.briarproject.bramble.system;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Process;
import android.os.SystemClock;

import org.briarproject.bramble.api.lifecycle.Service;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.AlarmListener;
import org.briarproject.bramble.api.system.AndroidWakeLock;
import org.briarproject.bramble.api.system.AndroidWakeLockManager;
import org.briarproject.bramble.api.system.TaskScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import static android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP;
import static android.app.AlarmManager.INTERVAL_FIFTEEN_MINUTES;
import static android.app.PendingIntent.FLAG_CANCEL_CURRENT;
import static android.content.Context.ALARM_SERVICE;
import static android.os.Build.VERSION.SDK_INT;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.system.AlarmConstants.EXTRA_PID;
import static org.briarproject.bramble.system.AlarmConstants.REQUEST_ALARM;

@ThreadSafe
@NotNullByDefault
class AndroidTaskScheduler implements TaskScheduler, Service, AlarmListener {

	private static final Logger LOG =
			getLogger(AndroidTaskScheduler.class.getName());

	private static final long TICK_MS = SECONDS.toMillis(10);
	private static final long ALARM_MS = INTERVAL_FIFTEEN_MINUTES;

	private final Application app;
	private final AndroidWakeLockManager wakeLockManager;
	private final ScheduledExecutorService scheduledExecutorService;
	private final AlarmManager alarmManager;

	private final Object lock = new Object();
	@GuardedBy("lock")
	private final Queue<ScheduledTask> tasks = new PriorityQueue<>();

	AndroidTaskScheduler(Application app,
			AndroidWakeLockManager wakeLockManager,
			ScheduledExecutorService scheduledExecutorService) {
		this.app = app;
		this.wakeLockManager = wakeLockManager;
		this.scheduledExecutorService = scheduledExecutorService;
		alarmManager = (AlarmManager)
				requireNonNull(app.getSystemService(ALARM_SERVICE));
	}

	@Override
	public void startService() {
		scheduledExecutorService.scheduleAtFixedRate(
				() -> wakeLockManager.runWakefully(this::runDueTasks),
				TICK_MS, TICK_MS, MILLISECONDS);
		scheduleAlarm();
	}

	@Override
	public void stopService() {
		cancelAlarm();
	}

	@Override
	public Future<?> schedule(Runnable task, Executor executor, long delay,
			TimeUnit unit) {
		long now = SystemClock.elapsedRealtime();
		long dueMillis = now + MILLISECONDS.convert(delay, unit);
		Runnable wakeful = createWakefulTask(task, executor);
		ScheduledTask s = new ScheduledTask(wakeful, dueMillis);
		if (dueMillis <= now) {
			scheduledExecutorService.execute(s);
		} else {
			synchronized (lock) {
				tasks.add(s);
			}
		}
		return s;
	}

	@Override
	public Future<?> scheduleWithFixedDelay(Runnable task, Executor executor,
			long delay, long interval, TimeUnit unit) {
		Runnable wrapped = () -> {
			task.run();
			scheduleWithFixedDelay(task, executor, interval, interval, unit);
		};
		return schedule(wrapped, executor, delay, unit);
	}

	@Override
	public void onAlarm(Intent intent) {
		wakeLockManager.runWakefully(() -> {
			int extraPid = intent.getIntExtra(EXTRA_PID, -1);
			int currentPid = Process.myPid();
			if (extraPid == currentPid) {
				LOG.info("Alarm");
				rescheduleAlarm();
				runDueTasks();
			} else {
				LOG.info("Ignoring alarm with PID " + extraPid
						+ ", current PID is " + currentPid);
			}
		});
	}

	private Runnable createWakefulTask(Runnable task, Executor executor) {
		// Hold a wake lock from before we submit the task until after it runs
		AndroidWakeLock wakeLock = wakeLockManager.createWakeLock();
		return () -> {
			wakeLock.acquire();
			executor.execute(() -> {
				try {
					task.run();
				} finally {
					wakeLock.release();
				}
			});
		};
	}

	private void runDueTasks() {
		long now = SystemClock.elapsedRealtime();
		List<ScheduledTask> due = new ArrayList<>();
		synchronized (lock) {
			while (true) {
				ScheduledTask s = tasks.peek();
				if (s == null || s.dueMillis > now) break;
				due.add(tasks.remove());
			}
		}
		if (LOG.isLoggable(INFO)) {
			LOG.info("Running " + due.size() + " due tasks");
		}
		for (ScheduledTask s : due) {
			if (LOG.isLoggable(INFO)) {
				LOG.info("Task is " + (now - s.dueMillis) + " ms overdue");
			}
			s.run();
		}
	}

	private void scheduleAlarm() {
		if (SDK_INT >= 23) scheduleIdleAlarm();
		else scheduleInexactRepeatingAlarm();
	}

	private void rescheduleAlarm() {
		if (SDK_INT >= 23) scheduleIdleAlarm();
	}

	private void cancelAlarm() {
		alarmManager.cancel(getAlarmPendingIntent());
	}

	private void scheduleInexactRepeatingAlarm() {
		alarmManager.setInexactRepeating(ELAPSED_REALTIME_WAKEUP,
				SystemClock.elapsedRealtime() + ALARM_MS, ALARM_MS,
				getAlarmPendingIntent());
	}

	@TargetApi(23)
	private void scheduleIdleAlarm() {
		alarmManager.setAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP,
				SystemClock.elapsedRealtime() + ALARM_MS,
				getAlarmPendingIntent());
	}

	private PendingIntent getAlarmPendingIntent() {
		Intent i = new Intent(app, AlarmReceiver.class);
		i.putExtra(EXTRA_PID, android.os.Process.myPid());
		return PendingIntent.getBroadcast(app, REQUEST_ALARM, i,
				FLAG_CANCEL_CURRENT);
	}

	private static class ScheduledTask extends FutureTask<Void>
			implements Comparable<ScheduledTask> {

		private final long dueMillis;

		public ScheduledTask(Runnable runnable, long dueMillis) {
			super(runnable, null);
			this.dueMillis = dueMillis;
		}

		@Override
		public int compareTo(ScheduledTask s) {
			//noinspection UseCompareMethod
			if (dueMillis < s.dueMillis) return -1;
			if (dueMillis > s.dueMillis) return 1;
			return 0;
		}
	}
}
