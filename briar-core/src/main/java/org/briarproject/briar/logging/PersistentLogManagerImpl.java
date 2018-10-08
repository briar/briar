package org.briarproject.briar.logging;

import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.lifecycle.ShutdownManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.Scheduler;
import org.briarproject.briar.api.logging.PersistentLogManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

@ThreadSafe
@NotNullByDefault
class PersistentLogManagerImpl implements PersistentLogManager {

	private static final Logger LOG =
			Logger.getLogger(PersistentLogManagerImpl.class.getName());

	private static final String LOG_FILE = "briar.log";
	private static final String OLD_LOG_FILE = "briar.log.old";
	private static final long FLUSH_INTERVAL_MS = MINUTES.toMillis(5);

	private final ScheduledExecutorService scheduler;
	private final Executor ioExecutor;
	private final ShutdownManager shutdownManager;
	private final Formatter formatter;

	@Inject
	PersistentLogManagerImpl(@Scheduler ScheduledExecutorService scheduler,
			@IoExecutor Executor ioExecutor, ShutdownManager shutdownManager,
			Formatter formatter) {
		this.scheduler = scheduler;
		this.ioExecutor = ioExecutor;
		this.shutdownManager = shutdownManager;
		this.formatter = formatter;
	}

	@Override
	public Handler createLogHandler(File dir) throws IOException {
		File logFile = new File(dir, LOG_FILE);
		File oldLogFile = new File(dir, OLD_LOG_FILE);
		if (oldLogFile.exists() && !oldLogFile.delete())
			LOG.warning("Failed to delete old log file");
		if (logFile.exists() && !logFile.renameTo(oldLogFile))
			LOG.warning("Failed to rename log file");
		try {
			OutputStream out = new FileOutputStream(logFile);
			StreamHandler handler = new StreamHandler(out, formatter);
			scheduler.scheduleWithFixedDelay(() ->
							ioExecutor.execute(handler::flush),
					FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, MILLISECONDS);
			shutdownManager.addShutdownHook(handler::flush);
			return handler;
		} catch (SecurityException e) {
			throw new IOException(e);
		}
	}

	@Override
	public Collection<String> getPersistedLog(File dir) throws IOException {
		File oldLogFile = new File(dir, OLD_LOG_FILE);
		if (oldLogFile.exists()) {
			LOG.info("Reading old log file");
			List<String> lines = new ArrayList<>();
			Scanner s = new Scanner(oldLogFile);
			while (s.hasNextLine()) lines.add(s.nextLine());
			s.close();
			return lines;
		} else {
			LOG.info("Old log file does not exist");
			return emptyList();
		}
	}
}
