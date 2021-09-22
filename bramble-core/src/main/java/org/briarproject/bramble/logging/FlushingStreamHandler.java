package org.briarproject.bramble.logging;

import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.system.TaskScheduler;

import java.io.OutputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

class FlushingStreamHandler extends StreamHandler {

	private static final int FLUSH_DELAY_MS = 5_000;

	private final TaskScheduler scheduler;
	private final Executor ioExecutor;
	private final AtomicBoolean flushScheduled = new AtomicBoolean(false);

	FlushingStreamHandler(TaskScheduler scheduler,
			Executor ioExecutor, OutputStream out, Formatter formatter) {
		super(out, formatter);
		this.scheduler = scheduler;
		this.ioExecutor = ioExecutor;
	}

	@Override
	public void publish(LogRecord record) {
		super.publish(record);
		if (!flushScheduled.getAndSet(true)) {
			scheduler.schedule(this::scheduledFlush, ioExecutor,
					FLUSH_DELAY_MS, MILLISECONDS);
		}
	}

	@IoExecutor
	private void scheduledFlush() {
		flushScheduled.set(false);
		flush();
	}
}
