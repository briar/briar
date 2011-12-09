package net.sf.briar.api.plugins;

import java.util.concurrent.ScheduledExecutorService;

public interface StreamPluginFactory {

	StreamPlugin createPlugin(ScheduledExecutorService pluginExecutor,
			StreamPluginCallback callback);
}
