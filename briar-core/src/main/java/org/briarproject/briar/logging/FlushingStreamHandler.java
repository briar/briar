package org.briarproject.briar.logging;

import java.io.OutputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

class FlushingStreamHandler extends StreamHandler {

	private static final int FLUSH_DELAY_MS = 5_000;

	private final ScheduledExecutorService scheduler;
	private final Executor ioExecutor;
	private final AtomicBoolean flushScheduled = new AtomicBoolean(false);

	FlushingStreamHandler(ScheduledExecutorService scheduler,
			Executor ioExecutor, OutputStream out, Formatter formatter) {
		super(out, formatter);
		this.scheduler = scheduler;
		this.ioExecutor = ioExecutor;
	}

	@Override
	public void publish(LogRecord record) {
		super.publish(record);
		if (!flushScheduled.getAndSet(true)) {
			scheduler.schedule(this::scheduledFlush,
					FLUSH_DELAY_MS, MILLISECONDS);
		}
	}

	private void scheduledFlush() {
		ioExecutor.execute(() -> {
			flushScheduled.set(false);
			flush();
		});
	}
}
