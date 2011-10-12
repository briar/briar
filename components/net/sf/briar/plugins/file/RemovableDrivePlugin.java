package net.sf.briar.plugins.file;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.TransportId;
import net.sf.briar.api.plugins.BatchTransportCallback;

class RemovableDrivePlugin extends FilePlugin
implements RemovableDriveMonitor.Callback {

	public static final int TRANSPORT_ID = 0;

	private static final TransportId id = new TransportId(TRANSPORT_ID);
	private static final Logger LOG =
		Logger.getLogger(RemovableDrivePlugin.class.getName());

	private final RemovableDriveFinder finder;
	private final RemovableDriveMonitor monitor;

	RemovableDrivePlugin(Executor executor, BatchTransportCallback callback,
			RemovableDriveFinder finder, RemovableDriveMonitor monitor) {
		super(executor, callback);
		this.finder = finder;
		this.monitor = monitor;
	}

	public TransportId getId() {
		return id;
	}

	@Override
	public synchronized void start() throws IOException {
		super.start();
		monitor.start(this);
	}

	@Override
	public synchronized void stop() throws IOException {
		super.stop();
		monitor.stop();
	}

	public boolean shouldPoll() {
		return false;
	}

	public long getPollingInterval() {
		return 0L;
	}

	public void poll() {
		throw new UnsupportedOperationException();
	}

	@Override
	protected File chooseOutputDirectory() {
		try {
			List<File> drives = finder.findRemovableDrives();
			if(drives.isEmpty()) return null;
			String[] paths = new String[drives.size()];
			for(int i = 0; i < paths.length; i++) {
				paths[i] = drives.get(i).getPath();
			}
			int i = callback.showChoice(paths, "REMOVABLE_DRIVE_CHOOSE_DRIVE");
			if(i == -1) return null;
			return drives.get(i);
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
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

	public void driveInserted(File root) {
		File[] files = root.listFiles();
		if(files != null) for(File f : files) createReaderFromFile(f);
	}
}
