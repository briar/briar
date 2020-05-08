package org.briarproject.bramble.io;

import org.briarproject.bramble.api.io.TimeoutMonitor;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.Scheduler;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

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

	private final Executor ioExecutor;
	private final Clock clock;

	private final List<TimeoutInputStream> streams =
			new CopyOnWriteArrayList<>();

	@Inject
	TimeoutMonitorImpl(@IoExecutor Executor ioExecutor, Clock clock,
			@Scheduler ScheduledExecutorService scheduler) {
		this.ioExecutor = ioExecutor;
		this.clock = clock;
		scheduler.scheduleWithFixedDelay(this::checkTimeouts,
				CHECK_INTERVAL_MS, CHECK_INTERVAL_MS, MILLISECONDS);
	}

	@Override
	public InputStream createTimeoutInputStream(InputStream in,
			long timeoutMs) {
		TimeoutInputStream stream = new TimeoutInputStream(clock, in,
				timeoutMs * 1_000_000, streams::remove);
		streams.add(stream);
		return stream;
	}

	@Scheduler
	private void checkTimeouts() {
		ioExecutor.execute(() -> {
			LOG.info("Checking input stream timeouts");
			for (TimeoutInputStream stream : streams) {
				if (stream.hasTimedOut()) {
					LOG.info("Input stream has timed out");
					try {
						stream.close();
					} catch (IOException e) {
						logException(LOG, INFO, e);
					}
				}
			}
		});
	}
}
