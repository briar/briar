package net.sf.briar.api.plugins.simplex;

import java.util.concurrent.Executor;

import net.sf.briar.clock.Clock;

public interface SimplexPluginFactory {

	SimplexPlugin createPlugin(Executor pluginExecutor, Clock clock,
			SimplexPluginCallback callback);
}
