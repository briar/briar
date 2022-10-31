package org.briarproject.bramble.test;

import org.briarproject.nullsafety.NotNullByDefault;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
@NotNullByDefault
public class TestLogFormatter extends SimpleFormatter {

	private final Object lock = new Object();
	private final DateFormat dateFormat; // Locking: lock
	private final Date date; // Locking: lock

	public static void use() {
		LogManager.getLogManager().reset();
		Logger rootLogger = LogManager.getLogManager().getLogger("");
		ConsoleHandler handler = new ConsoleHandler();
		handler.setFormatter(new TestLogFormatter());
		rootLogger.addHandler(handler);
	}

	private TestLogFormatter() {
		synchronized (lock) {
			dateFormat = new SimpleDateFormat("HH:mm:ss.SSS");
			date = new Date();
		}
	}

	@Override
	public String format(LogRecord rec) {
		if (rec.getThrown() == null) {
			String dateString;
			synchronized (lock) {
				date.setTime(rec.getMillis());
				dateString = dateFormat.format(date);
			}
			return String.format("%s [%s] %s %s - %s\n",
					dateString,
					Thread.currentThread().getName(),
					rec.getLevel().getName(),
					rec.getLoggerName(),
					rec.getMessage());
		} else {
			return super.format(rec);
		}
	}
}
