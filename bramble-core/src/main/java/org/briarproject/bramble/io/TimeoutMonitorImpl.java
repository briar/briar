package org.briarproject.bramble.io;

import org.briarproject.bramble.api.io.TimeoutMonitor;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.TaskScheduler;
import org.briarproject.bramble.api.system.TaskScheduler.Cancellable;
import org.briarproject.bramble.api.system.Wakeful;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;

class TimeoutMonitorImpl implements TimeoutMonitor {

	private static final Logger LOG =
			getLogger(TimeoutMonitorImpl.class.getName());

	private static final long CHECK_INTERVAL_MS = SECONDS.toMillis(10);

	private final TaskScheduler scheduler;
	private final Executor ioExecutor;
	private final Clock clock;
	private final Object lock = new Object();
	@GuardedBy("lock")
	private final List<TimeoutInputStream> streams = new ArrayList<>();

	@GuardedBy("lock")
	private Cancellable cancellable = null;

	@Inject
	TimeoutMonitorImpl(TaskScheduler scheduler,
			@IoExecutor Executor ioExecutor, Clock clock) {
		this.scheduler = scheduler;
		this.ioExecutor = ioExecutor;
		this.clock = clock;
	}

	@Override
	public InputStream createTimeoutInputStream(InputStream in,
			long timeoutMs) {
		TimeoutInputStream stream = new TimeoutInputStream(clock, in,
				timeoutMs, this::removeStream);
		synchronized (lock) {
			if (streams.isEmpty()) {
				cancellable = scheduler.scheduleWithFixedDelay(
						this::checkTimeouts, ioExecutor, CHECK_INTERVAL_MS,
						CHECK_INTERVAL_MS, MILLISECONDS);
			}
			streams.add(stream);
		}
		return stream;
	}

	private void removeStream(TimeoutInputStream stream) {
		Cancellable toCancel = null;
		synchronized (lock) {
			if (streams.remove(stream) && streams.isEmpty()) {
				toCancel = cancellable;
				cancellable = null;
			}
		}
		if (toCancel != null) {
			LOG.info("Cancelling timeout monitor task");
			toCancel.cancel();
		}
	}

	@IoExecutor
	@Wakeful
	private void checkTimeouts() {
		List<TimeoutInputStream> snapshot;
		synchronized (lock) {
			snapshot = new ArrayList<>(streams);
		}
		for (TimeoutInputStream stream : snapshot) {
			if (stream.hasTimedOut()) {
				LOG.info("Input stream has timed out");
				try {
					stream.close();
				} catch (IOException e) {
					logException(LOG, INFO, e);
				}
			}
		}
	}
}
