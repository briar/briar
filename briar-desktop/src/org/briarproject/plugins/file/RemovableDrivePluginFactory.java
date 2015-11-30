package org.briarproject.plugins.file;

import java.util.concurrent.Executor;

import org.briarproject.api.TransportId;
import org.briarproject.api.plugins.simplex.SimplexPlugin;
import org.briarproject.api.plugins.simplex.SimplexPluginCallback;
import org.briarproject.api.plugins.simplex.SimplexPluginFactory;
import org.briarproject.api.system.FileUtils;
import org.briarproject.util.OsUtils;

public class RemovableDrivePluginFactory implements SimplexPluginFactory {

	// Maximum latency 14 days (Royal Mail or lackadaisical carrier pigeon)
	private static final int MAX_LATENCY = 14 * 24 * 60 * 60 * 1000;
	private static final int POLLING_INTERVAL = 10 * 1000; // 10 seconds

	private final Executor ioExecutor;
	private final FileUtils fileUtils;

	public RemovableDrivePluginFactory(Executor ioExecutor,
			FileUtils fileUtils) {
		this.ioExecutor = ioExecutor;
		this.fileUtils = fileUtils;
	}

	public TransportId getId() {
		return RemovableDrivePlugin.ID;
	}

	public SimplexPlugin createPlugin(SimplexPluginCallback callback) {
		RemovableDriveFinder finder;
		RemovableDriveMonitor monitor;
		if (OsUtils.isLinux()) {
			finder = new LinuxRemovableDriveFinder();
			monitor = new LinuxRemovableDriveMonitor();
		} else if (OsUtils.isMacLeopardOrNewer()) {
			finder = new MacRemovableDriveFinder();
			monitor = new MacRemovableDriveMonitor();
		} else if (OsUtils.isMac()) {
			// JNotify requires OS X 10.5 or newer, so we have to poll
			finder = new MacRemovableDriveFinder();
			monitor = new PollingRemovableDriveMonitor(ioExecutor, finder,
					POLLING_INTERVAL);
		} else if (OsUtils.isWindows()) {
			finder = new WindowsRemovableDriveFinder();
			monitor = new PollingRemovableDriveMonitor(ioExecutor, finder,
					POLLING_INTERVAL);
		} else {
			return null;
		}
		return new RemovableDrivePlugin(ioExecutor, fileUtils, callback,
				finder, monitor, MAX_LATENCY);
	}
}
