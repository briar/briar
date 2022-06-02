package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.connection.ConnectionManager;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.mailbox.MailboxDirectory;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.plugin.TransportConnectionReader;
import org.briarproject.bramble.api.plugin.event.TransportActiveEvent;
import org.briarproject.bramble.api.plugin.simplex.SimplexPlugin;
import org.briarproject.bramble.api.properties.TransportProperties;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState.RUNNING;
import static org.briarproject.bramble.api.mailbox.MailboxConstants.ID;
import static org.briarproject.bramble.api.nullsafety.NullSafety.requireNonNull;
import static org.briarproject.bramble.api.plugin.file.FileConstants.PROP_PATH;
import static org.briarproject.bramble.util.LogUtils.logException;

@ThreadSafe
@NotNullByDefault
class MailboxFileManagerImpl implements MailboxFileManager, EventListener {

	private static final Logger LOG =
			getLogger(MailboxFileManagerImpl.class.getName());

	// Package access for testing
	static final String DOWNLOAD_DIR_NAME = "downloads";

	private final Executor ioExecutor;
	private final PluginManager pluginManager;
	private final ConnectionManager connectionManager;
	private final LifecycleManager lifecycleManager;
	private final File mailboxDir;
	private final EventBus eventBus;
	private final CountDownLatch orphanLatch = new CountDownLatch(1);

	@Inject
	MailboxFileManagerImpl(@IoExecutor Executor ioExecutor,
			PluginManager pluginManager,
			ConnectionManager connectionManager,
			LifecycleManager lifecycleManager,
			@MailboxDirectory File mailboxDir,
			EventBus eventBus) {
		this.ioExecutor = ioExecutor;
		this.pluginManager = pluginManager;
		this.connectionManager = connectionManager;
		this.lifecycleManager = lifecycleManager;
		this.mailboxDir = mailboxDir;
		this.eventBus = eventBus;
	}

	@Override
	public File createTempFileForDownload() throws IOException {
		// Wait for orphaned files to be handled before creating new files
		try {
			orphanLatch.await();
		} catch (InterruptedException e) {
			throw new IOException(e);
		}
		File downloadDir = createDirectoryIfNeeded(DOWNLOAD_DIR_NAME);
		return File.createTempFile("mailbox", ".tmp", downloadDir);
	}

	private File createDirectoryIfNeeded(String name) throws IOException {
		File dir = new File(mailboxDir, name);
		//noinspection ResultOfMethodCallIgnored
		dir.mkdirs();
		if (!dir.isDirectory()) {
			throw new IOException("Failed to create directory '" + name + "'");
		}
		return dir;
	}

	@Override
	public void handleDownloadedFile(File f) {
		// We shouldn't reach this point until the plugin has been started
		SimplexPlugin plugin =
				(SimplexPlugin) requireNonNull(pluginManager.getPlugin(ID));
		TransportProperties p = new TransportProperties();
		p.put(PROP_PATH, f.getAbsolutePath());
		TransportConnectionReader reader = plugin.createReader(p);
		if (reader == null) {
			LOG.warning("Failed to create reader for downloaded file");
			return;
		}
		TransportConnectionReader decorated = new MailboxFileReader(reader, f);
		LOG.info("Reading downloaded file");
		connectionManager.manageIncomingConnection(ID, decorated,
				exception -> isHandlingComplete(exception, true));
	}

	private boolean isHandlingComplete(boolean exception, boolean recognised) {
		// If we've successfully read the file then we're done
		if (!exception && recognised) return true;
		// If the app is shutting down we may get spurious IO exceptions
		// due to executors being shut down. Leave the file in the download
		// directory and we'll try to read it again at the next startup
		return !lifecycleManager.getLifecycleState().isAfter(RUNNING);
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof TransportActiveEvent) {
			TransportActiveEvent t = (TransportActiveEvent) e;
			if (t.getTransportId().equals(ID)) {
				ioExecutor.execute(this::handleOrphanedFiles);
				eventBus.removeListener(this);
			}
		}
	}

	/**
	 * This method is called at startup, as soon as the plugin is started, to
	 * handle any files that were left in the download directory at the last
	 * shutdown.
	 */
	@IoExecutor
	private void handleOrphanedFiles() {
		try {
			File downloadDir = createDirectoryIfNeeded(DOWNLOAD_DIR_NAME);
			File[] orphans = downloadDir.listFiles();
			// Now that we've got the list of orphans, new files can be created
			orphanLatch.countDown();
			if (orphans != null) for (File f : orphans) handleDownloadedFile(f);
		} catch (IOException e) {
			logException(LOG, WARNING, e);
		}
	}

	private class MailboxFileReader implements TransportConnectionReader {

		private final TransportConnectionReader delegate;
		private final File file;

		private MailboxFileReader(TransportConnectionReader delegate,
				File file) {
			this.delegate = delegate;
			this.file = file;
		}

		@Override
		public InputStream getInputStream() throws IOException {
			return delegate.getInputStream();
		}

		@Override
		public void dispose(boolean exception, boolean recognised)
				throws IOException {
			delegate.dispose(exception, recognised);
			if (isHandlingComplete(exception, recognised)) {
				LOG.info("Deleting downloaded file");
				if (!file.delete()) {
					LOG.warning("Failed to delete downloaded file");
				}
			}
		}
	}
}
