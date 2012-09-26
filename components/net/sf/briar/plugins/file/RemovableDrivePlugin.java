package net.sf.briar.plugins.file;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.simplex.SimplexPluginCallback;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.util.StringUtils;

class RemovableDrivePlugin extends FilePlugin
implements RemovableDriveMonitor.Callback {

	public static final byte[] TRANSPORT_ID =
		StringUtils.fromHexString("7c81bf5c9b1cd557685548c85f976bbd"
				+ "e633d2418ea2e230e5710fb43c6f8cc0"
				+ "68abca3a9d0edb13bcea13b851725c5d");

	private static final TransportId ID = new TransportId(TRANSPORT_ID);
	private static final Logger LOG =
		Logger.getLogger(RemovableDrivePlugin.class.getName());

	private final RemovableDriveFinder finder;
	private final RemovableDriveMonitor monitor;

	RemovableDrivePlugin(@PluginExecutor Executor pluginExecutor,
			SimplexPluginCallback callback, RemovableDriveFinder finder,
			RemovableDriveMonitor monitor) {
		super(pluginExecutor, callback);
		this.finder = finder;
		this.monitor = monitor;
	}

	public TransportId getId() {
		return ID;
	}

	public void start() throws IOException {
		running = true;
		monitor.start(this);
	}

	public void stop() throws IOException {
		running = false;
		monitor.stop();
	}

	public boolean shouldPoll() {
		return false;
	}

	public long getPollingInterval() {
		throw new UnsupportedOperationException();
	}

	public void poll(Collection<ContactId> connected) {
		throw new UnsupportedOperationException();
	}

	@Override
	protected File chooseOutputDirectory() {
		try {
			List<File> drives =
				new ArrayList<File>(finder.findRemovableDrives());
			if(drives.isEmpty()) return null;
			String[] paths = new String[drives.size()];
			for(int i = 0; i < paths.length; i++) {
				paths[i] = drives.get(i).getPath();
			}
			int i = callback.showChoice(paths, "REMOVABLE_DRIVE_CHOOSE_DRIVE");
			if(i == -1) return null;
			return drives.get(i);
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
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
		List<File> matches = new ArrayList<File>();
		try {
			for(File drive : finder.findRemovableDrives()) {
				File[] files = drive.listFiles();
				if(files != null) {
					for(File f : files) {
						if(f.isFile() && filename.equals(f.getName()))
							matches.add(f);
					}
				}
			}
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
		}
		return Collections.unmodifiableList(matches);
	}

	public void driveInserted(File root) {
		File[] files = root.listFiles();
		if(files != null) {
			for(File f : files) if(f.isFile()) createReaderFromFile(f);
		}
	}

	public void exceptionThrown(IOException e) {
		if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
	}
}
