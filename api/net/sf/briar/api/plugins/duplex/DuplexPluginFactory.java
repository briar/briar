package net.sf.briar.api.plugins.duplex;

import java.util.concurrent.Executor;

import net.sf.briar.clock.Clock;

public interface DuplexPluginFactory {

	DuplexPlugin createPlugin(Executor pluginExecutor, Clock clock,
			DuplexPluginCallback callback);
}
