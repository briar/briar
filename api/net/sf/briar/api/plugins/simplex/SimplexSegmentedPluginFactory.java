package net.sf.briar.api.plugins.simplex;

import java.util.concurrent.Executor;

public interface SimplexSegmentedPluginFactory {

	SimplexSegmentedPlugin createPlugin(Executor pluginExecutor,
			SimplexSegmentedPluginCallback callback);
}
