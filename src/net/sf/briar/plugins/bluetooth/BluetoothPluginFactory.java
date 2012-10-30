package net.sf.briar.plugins.bluetooth;

import java.util.concurrent.Executor;

import net.sf.briar.api.clock.SystemClock;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexPluginFactory;

public class BluetoothPluginFactory implements DuplexPluginFactory {

	private static final long POLLING_INTERVAL = 3L * 60L * 1000L; // 3 mins

	public DuplexPlugin createPlugin(@PluginExecutor Executor pluginExecutor,
			DuplexPluginCallback callback) {
		return new BluetoothPlugin(pluginExecutor, new SystemClock(), callback,
				POLLING_INTERVAL);
	}
}
