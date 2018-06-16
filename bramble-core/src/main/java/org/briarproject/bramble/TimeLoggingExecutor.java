package org.briarproject.bramble;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;
import static org.briarproject.bramble.util.LogUtils.now;

@NotNullByDefault
public class TimeLoggingExecutor extends ThreadPoolExecutor {

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
	public void execute(Runnable r) {
		if (log.isLoggable(FINE)) {
			long submitted = now();
			super.execute(() -> {
				long started = now();
				long queued = started - submitted;
				log.fine("Queue time " + queued + " ms");
				r.run();
				long executing = now() - started;
				log.fine("Execution time " + executing + " ms");
			});
		} else {
			super.execute(r);
		}
	}
}
