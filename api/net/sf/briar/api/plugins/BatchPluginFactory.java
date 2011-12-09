package net.sf.briar.api.plugins;

import java.util.concurrent.ScheduledExecutorService;

public interface BatchPluginFactory {

	BatchPlugin createPlugin(ScheduledExecutorService pluginExecutor,
			BatchPluginCallback callback);
}
