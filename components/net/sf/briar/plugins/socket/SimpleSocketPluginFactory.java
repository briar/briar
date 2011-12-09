package net.sf.briar.plugins.socket;

import java.util.concurrent.Executor;

import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.StreamPlugin;
import net.sf.briar.api.plugins.StreamPluginCallback;
import net.sf.briar.api.plugins.StreamPluginFactory;

public class SimpleSocketPluginFactory implements StreamPluginFactory {

	private static final long POLLING_INTERVAL = 5L * 60L * 1000L; // 5 mins

	public StreamPlugin createPlugin(@PluginExecutor Executor pluginExecutor,
			StreamPluginCallback callback) {
		return new SimpleSocketPlugin(pluginExecutor, callback,
				POLLING_INTERVAL);
	}
}
