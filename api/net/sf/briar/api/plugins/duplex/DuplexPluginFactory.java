package net.sf.briar.api.plugins.duplex;

import java.util.concurrent.Executor;

public interface DuplexPluginFactory {

	DuplexPlugin createPlugin(Executor pluginExecutor,
			DuplexPluginCallback callback);
}
