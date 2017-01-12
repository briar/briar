package org.briarproject.bramble;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;

public class TimeLoggingExecutor extends ThreadPoolExecutor {

	private static final Level LOG_LEVEL = FINE;

	private final Logger log;

	public TimeLoggingExecutor(String tag, int corePoolSize, int maxPoolSize,
			long keepAliveTime, TimeUnit unit,
			BlockingQueue<Runnable> workQueue,
			RejectedExecutionHandler handler) {
		super(corePoolSize, maxPoolSize, keepAliveTime, unit, workQueue,
				handler);
		log = Logger.getLogger(tag);
	}

	@Override
	public void execute(final Runnable r) {
		final long submitted = System.currentTimeMillis();
		super.execute(new Runnable() {
			@Override
			public void run() {
				long started = System.currentTimeMillis();
				if (log.isLoggable(LOG_LEVEL)) {
					long duration = started - submitted;
					log.log(LOG_LEVEL, "Queue time " + duration + " ms");
				}
				r.run();
				long finished = System.currentTimeMillis();
				if (log.isLoggable(LOG_LEVEL)) {
					long duration = finished - started;
					log.log(LOG_LEVEL, "Execution time " + duration + " ms");
				}
			}
		});
	}
}
