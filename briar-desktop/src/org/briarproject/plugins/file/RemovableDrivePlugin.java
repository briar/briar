package org.briarproject.plugins.file;

import org.briarproject.api.TransportId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.plugins.simplex.SimplexPluginCallback;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;

@NotNullByDefault
class RemovableDrivePlugin extends FilePlugin
implements RemovableDriveMonitor.Callback {

	static final TransportId ID = new TransportId("file");

	private static final Logger LOG =
			Logger.getLogger(RemovableDrivePlugin.class.getName());

	private final RemovableDriveFinder finder;
	private final RemovableDriveMonitor monitor;

	RemovableDrivePlugin(Executor ioExecutor, SimplexPluginCallback callback,
			RemovableDriveFinder finder, RemovableDriveMonitor monitor,
			int maxLatency) {
		super(ioExecutor, callback, maxLatency);
		this.finder = finder;
		this.monitor = monitor;
	}

	@Override
	public TransportId getId() {
		return ID;
	}

	@Override
	public boolean start() throws IOException {
		if (used.getAndSet(true)) throw new IllegalStateException();
		running = true;
		monitor.start(this);
		return true;
	}

	@Override
	public void stop() throws IOException {
		running = false;
		monitor.stop();
	}

	@Override
	public boolean shouldPoll() {
		return false;
	}

	@Override
	public int getPollingInterval() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void poll(Collection<ContactId> connected) {
		throw new UnsupportedOperationException();
	}

	@Override
	protected File chooseOutputDirectory() {
		try {
			List<File> drives = new ArrayList<>(finder.findRemovableDrives());
			if (drives.isEmpty()) return null;
			String[] paths = new String[drives.size()];
			for (int i = 0; i < paths.length; i++) {
				paths[i] = drives.get(i).getPath();
			}
			int i = callback.showChoice(paths, "REMOVABLE_DRIVE_CHOOSE_DRIVE");
			if (i == -1) return null;
			return drives.get(i);
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return null;
		}
	}

	@Override
	protected void readerFinished(File f) {
		callback.showMessage("REMOVABLE_DRIVE_READ_FINISHED");
	}

	@Override
	protected void writerFinished(File f) {
		callback.showMessage("REMOVABLE_DRIVE_WRITE_FINISHED");
	}

	@Override
	protected Collection<File> findFilesByName(String filename) {
		List<File> matches = new ArrayList<>();
		try {
			for (File drive : finder.findRemovableDrives()) {
				File[] files = drive.listFiles();
				if (files != null) {
					for (File f : files) {
						if (f.isFile() && filename.equals(f.getName()))
							matches.add(f);
					}
				}
			}
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
		return matches;
	}

	@Override
	public void driveInserted(File root) {
		File[] files = root.listFiles();
		if (files != null) {
			for (File f : files) if (f.isFile()) createReaderFromFile(f);
		}
	}

	@Override
	public void exceptionThrown(IOException e) {
		if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
	}
}
