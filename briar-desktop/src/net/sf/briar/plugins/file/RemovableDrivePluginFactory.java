package net.sf.briar.plugins.file;

import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;

import java.util.concurrent.Executor;

import net.sf.briar.api.TransportId;
import net.sf.briar.api.os.FileUtils;
import net.sf.briar.api.plugins.simplex.SimplexPlugin;
import net.sf.briar.api.plugins.simplex.SimplexPluginCallback;
import net.sf.briar.api.plugins.simplex.SimplexPluginFactory;
import net.sf.briar.util.OsUtils;

public class RemovableDrivePluginFactory implements SimplexPluginFactory {

	// Maximum latency 14 days (Royal Mail or lackadaisical carrier pigeon)
	private static final long MAX_LATENCY = 14 * 24 * 60 * 60 * 1000;
	private static final long POLLING_INTERVAL = 10 * 1000; // 10 seconds

	private final Executor pluginExecutor;
	private final FileUtils fileUtils;

	public RemovableDrivePluginFactory(Executor pluginExecutor,
			FileUtils fileUtils) {
		this.pluginExecutor = pluginExecutor;
		this.fileUtils = fileUtils;
	}

	public TransportId getId() {
		return RemovableDrivePlugin.ID;
	}

	public SimplexPlugin createPlugin(SimplexPluginCallback callback) {
		RemovableDriveFinder finder;
		RemovableDriveMonitor monitor;
		if(OsUtils.isLinux()) {
			finder = new LinuxRemovableDriveFinder();
			monitor = new LinuxRemovableDriveMonitor();
		} else if(OsUtils.isMacLeopardOrNewer()) {
			finder = new MacRemovableDriveFinder();
			monitor = new MacRemovableDriveMonitor();
		} else if(OsUtils.isMac()) {
			// JNotify requires OS X 10.5 or newer, so we have to poll
			finder = new MacRemovableDriveFinder();
			monitor = new PollingRemovableDriveMonitor(pluginExecutor, finder,
					POLLING_INTERVAL);
		} else if(OsUtils.isWindows()) {
			finder = new WindowsRemovableDriveFinder();
			monitor = new PollingRemovableDriveMonitor(pluginExecutor, finder,
					POLLING_INTERVAL);
		} else {
			return null;
		}
		return new RemovableDrivePlugin(pluginExecutor, fileUtils, callback,
				finder, monitor, MAX_FRAME_LENGTH, MAX_LATENCY);
	}
}
