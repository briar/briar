package net.sf.briar.plugins.file;

import java.util.concurrent.Executor;

import net.sf.briar.api.plugins.BatchPluginCallback;
import net.sf.briar.api.plugins.BatchPlugin;
import net.sf.briar.api.plugins.BatchPluginFactory;
import net.sf.briar.util.OsUtils;

public class RemovableDrivePluginFactory implements BatchPluginFactory {

	private static final long POLLING_INTERVAL = 10L * 1000L; // 10 seconds

	public BatchPlugin createPlugin(Executor executor,
			BatchPluginCallback callback) {
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
			monitor = new PollingRemovableDriveMonitor(finder,
					POLLING_INTERVAL);
		} else if(OsUtils.isWindows()) {
			finder = new WindowsRemovableDriveFinder();
			monitor = new PollingRemovableDriveMonitor(finder,
					POLLING_INTERVAL);
		} else {
			return null;
		}
		return new RemovableDrivePlugin(executor, callback, finder, monitor);
	}
}
