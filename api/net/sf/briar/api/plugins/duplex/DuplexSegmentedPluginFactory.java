package net.sf.briar.api.plugins.duplex;

import java.util.concurrent.Executor;

public interface DuplexSegmentedPluginFactory {

	DuplexSegmentedPlugin createPlugin(Executor pluginExecutor,
			DuplexSegmentedPluginCallback callback);

}
