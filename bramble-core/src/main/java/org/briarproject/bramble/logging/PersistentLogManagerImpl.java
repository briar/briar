package org.briarproject.bramble.logging;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager.OpenDatabaseHook;
import org.briarproject.bramble.api.lifecycle.ShutdownManager;
import org.briarproject.bramble.api.logging.PersistentLogManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.system.TaskScheduler;
import org.briarproject.bramble.api.transport.StreamReaderFactory;
import org.briarproject.bramble.api.transport.StreamWriter;
import org.briarproject.bramble.api.transport.StreamWriterFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Scanner;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;

@ThreadSafe
@NotNullByDefault
class PersistentLogManagerImpl implements PersistentLogManager,
		OpenDatabaseHook {

	private static final Logger LOG =
			getLogger(PersistentLogManagerImpl.class.getName());

	private static final String LOG_FILE = "briar.log";
	private static final String OLD_LOG_FILE = "briar.log.old";

	private final TaskScheduler scheduler;
	private final Executor ioExecutor;
	private final ShutdownManager shutdownManager;
	private final DatabaseComponent db;
	private final StreamReaderFactory streamReaderFactory;
	private final StreamWriterFactory streamWriterFactory;
	private final Formatter formatter;
	private final SecretKey logKey;
	private final AtomicReference<Integer> shutdownHookHandle =
			new AtomicReference<>();

	@Nullable
	private volatile SecretKey oldLogKey = null;

	@Inject
	PersistentLogManagerImpl(
			TaskScheduler scheduler,
			@IoExecutor Executor ioExecutor,
			ShutdownManager shutdownManager,
			DatabaseComponent db,
			StreamReaderFactory streamReaderFactory,
			StreamWriterFactory streamWriterFactory,
			Formatter formatter,
			CryptoComponent crypto) {
		this.scheduler = scheduler;
		this.ioExecutor = ioExecutor;
		this.shutdownManager = shutdownManager;
		this.db = db;
		this.streamReaderFactory = streamReaderFactory;
		this.streamWriterFactory = streamWriterFactory;
		this.formatter = formatter;
		logKey = crypto.generateSecretKey();
	}

	@Override
	public void onDatabaseOpened(Transaction txn) throws DbException {
		Settings s = db.getSettings(txn, LOG_SETTINGS_NAMESPACE);
		// Load the old log key, if any
		byte[] oldKeyBytes = s.getBytes(LOG_KEY_KEY);
		if (oldKeyBytes != null && oldKeyBytes.length == SecretKey.LENGTH) {
			LOG.info("Loaded old log key");
			oldLogKey = new SecretKey(oldKeyBytes);
		}
		// Store the current log key
		s.putBytes(LOG_KEY_KEY, logKey.getBytes());
		db.mergeSettings(txn, s, LOG_SETTINGS_NAMESPACE);
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
			StreamWriter writer =
					streamWriterFactory.createLogStreamWriter(out, logKey);
			StreamHandler handler = new FlushingStreamHandler(scheduler,
					ioExecutor, writer.getOutputStream(), formatter);
			// Flush the log and terminate the stream at shutdown
			Runnable shutdownHook = () -> {
				LOG.info("Shutting down");
				handler.flush();
				try {
					writer.sendEndOfStream();
				} catch (IOException e) {
					logException(LOG, WARNING, e);
				}
			};
			int handle = shutdownManager.addShutdownHook(shutdownHook);
			// If a previous handler registered a shutdown hook, remove it
			Integer oldHandle = shutdownHookHandle.getAndSet(handle);
			if (oldHandle != null) {
				shutdownManager.removeShutdownHook(oldHandle);
			}
			return handler;
		} catch (SecurityException e) {
			throw new IOException(e);
		}
	}

	@Override
	public void addLogHandler(File dir, Logger logger) throws IOException {
		for (Handler h : logger.getHandlers()) {
			if (h instanceof FlushingStreamHandler) logger.removeHandler(h);
		}
		logger.addHandler(createLogHandler(dir));
	}

	@Override
	public Scanner getPersistentLog(File dir, boolean old)
			throws IOException {
		if (old) {
			SecretKey oldLogKey = this.oldLogKey;
			if (oldLogKey == null) {
				LOG.info("Old log key has not been loaded");
				return emptyScanner();
			}
			return getPersistentLog(new File(dir, OLD_LOG_FILE), oldLogKey);
		} else {
			return getPersistentLog(new File(dir, LOG_FILE), logKey);
		}
	}

	private Scanner getPersistentLog(File logFile, SecretKey key)
			throws IOException {
		if (logFile.exists()) {
			LOG.info("Reading log file");
			InputStream in = new FileInputStream(logFile);
			return new Scanner(streamReaderFactory.createLogStreamReader(in,
					key));
		} else {
			LOG.info("Log file does not exist");
			return emptyScanner();
		}
	}

	private Scanner emptyScanner() {
		return new Scanner(new ByteArrayInputStream(new byte[0]));
	}
}
