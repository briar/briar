package net.sf.briar.plugins.bluetooth;

import java.util.concurrent.Executor;

import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.DuplexPlugin;
import net.sf.briar.api.plugins.DuplexPluginCallback;
import net.sf.briar.api.plugins.DuplexPluginFactory;

public class BluetoothPluginFactory implements DuplexPluginFactory {

	private static final long POLLING_INTERVAL = 3L * 60L * 1000L; // 3 mins

	public DuplexPlugin createPlugin(@PluginExecutor Executor pluginExecutor,
			DuplexPluginCallback callback) {
		return new BluetoothPlugin(pluginExecutor, callback, POLLING_INTERVAL);
	}
}
