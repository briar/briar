package net.sf.briar.api.plugins.simplex;

import java.util.concurrent.Executor;

public interface SimplexPluginFactory {

	SimplexPlugin createPlugin(Executor pluginExecutor,
			SimplexPluginCallback callback);
}
