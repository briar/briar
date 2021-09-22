package org.briarproject.bramble.util;

import java.io.File;
import java.util.Collection;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;

public class LogUtils {

	private static final int NANOS_PER_MILLI = 1000 * 1000;

	/**
	 * Returns the elapsed time in milliseconds since some arbitrary
	 * starting time. This is only useful for measuring elapsed time.
	 */
	public static long now() {
		return System.nanoTime() / NANOS_PER_MILLI;
	}

	/**
	 * Logs the duration of a task.
	 *
	 * @param logger the logger to use
	 * @param task a description of the task
	 * @param start the start time of the task, as returned by {@link #now()}
	 */
	public static void logDuration(Logger logger, String task, long start) {
		if (logger.isLoggable(FINE)) {
			long duration = now() - start;
			logger.fine(task + " took " + duration + " ms");
		}
	}

	public static void logException(Logger logger, Level level, Throwable t) {
		if (logger.isLoggable(level)) logger.log(level, t.toString(), t);
	}

	public static void logFileOrDir(Logger logger, Level level, File f) {
		if (logger.isLoggable(level)) {
			if (f.isFile()) {
				logWithType(logger, level, f, "F");
			} else if (f.isDirectory()) {
				logWithType(logger, level, f, "D");
				File[] children = f.listFiles();
				if (children != null) {
					for (File child : children)
						logFileOrDir(logger, level, child);
				}
			} else if (f.exists()) {
				logWithType(logger, level, f, "?");
			}
		}
	}

	private static void logWithType(Logger logger, Level level, File f,
			String type) {
		logger.log(level, type + " " + f.getAbsolutePath() + " " + f.length());
	}

	public static String formatLog(Formatter formatter,
			Collection<LogRecord> logRecords) {
		StringBuilder sb = new StringBuilder();
		for (LogRecord record : logRecords) {
			sb.append(formatter.format(record)).append('\n');
		}
		return sb.toString();
	}
}
