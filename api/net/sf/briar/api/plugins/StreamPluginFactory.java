package net.sf.briar.api.plugins;

import java.util.concurrent.Executor;

public interface StreamPluginFactory {

	StreamPlugin createPlugin(Executor pluginExecutor,
			StreamPluginCallback callback);
}
