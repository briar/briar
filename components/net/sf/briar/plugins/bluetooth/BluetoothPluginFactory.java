package net.sf.briar.plugins.bluetooth;

import java.util.concurrent.ScheduledExecutorService;

import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.StreamPlugin;
import net.sf.briar.api.plugins.StreamPluginCallback;
import net.sf.briar.api.plugins.StreamPluginFactory;

public class BluetoothPluginFactory implements StreamPluginFactory {

	private static final long POLLING_INTERVAL = 3L * 60L * 1000L; // 3 mins

	public StreamPlugin createPlugin(
			@PluginExecutor ScheduledExecutorService pluginExecutor,
			StreamPluginCallback callback) {
		return new BluetoothPlugin(pluginExecutor, callback, POLLING_INTERVAL);
	}
}
