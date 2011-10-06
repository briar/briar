package net.sf.briar.plugins.file;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.transport.InvalidConfigException;
import net.sf.briar.api.transport.InvalidTransportException;
import net.sf.briar.api.transport.batch.BatchTransportCallback;

class RemovableDrivePlugin extends FilePlugin
implements RemovableDriveMonitor.Callback {

	public static final int TRANSPORT_ID = 0;

	private static final TransportId id = new TransportId(TRANSPORT_ID);

	private final RemovableDriveFinder finder;
	private final RemovableDriveMonitor monitor;

	RemovableDrivePlugin(Executor executor, RemovableDriveFinder finder,
			RemovableDriveMonitor monitor) {
		super(executor);
		this.finder = finder;
		this.monitor = monitor;
	}

	public TransportId getId() {
		return id;
	}

	@Override
	public void start(Map<String, String> localProperties,
			Map<ContactId, Map<String, String>> remoteProperties,
			Map<String, String> config, BatchTransportCallback callback)
	throws InvalidTransportException, InvalidConfigException, IOException {
		super.start(localProperties, remoteProperties, config, callback);
		monitor.start(this);
	}

	@Override
	public void stop() throws IOException {
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
			int i = callback.showChoice("REMOVABLE_DRIVE_CHOOSE_DRIVE", paths);
			if(i == -1) return null;
			return drives.get(i);
		} catch(IOException e) {
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
