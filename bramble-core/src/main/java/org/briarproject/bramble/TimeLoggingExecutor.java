package org.briarproject.bramble;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;

@NotNullByDefault
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
		if (log.isLoggable(LOG_LEVEL)) {
			final long submitted = System.currentTimeMillis();
			super.execute(new Runnable() {
				@Override
				public void run() {
					long started = System.currentTimeMillis();
					long queued = started - submitted;
					log.log(LOG_LEVEL, "Queue time " + queued + " ms");
					r.run();
					long executing = System.currentTimeMillis() - started;
					log.log(LOG_LEVEL, "Execution time " + executing + " ms");
				}
			});
		} else {
			super.execute(r);
		}
	}
}
