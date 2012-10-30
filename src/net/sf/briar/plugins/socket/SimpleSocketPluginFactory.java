package net.sf.briar.plugins.socket;

import java.util.concurrent.Executor;

import net.sf.briar.api.android.AndroidExecutor;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexPluginFactory;
import android.content.Context;

public class SimpleSocketPluginFactory implements DuplexPluginFactory {

	private static final long POLLING_INTERVAL = 5L * 60L * 1000L; // 5 mins

	public DuplexPlugin createPlugin(@PluginExecutor Executor pluginExecutor,
			AndroidExecutor androidExecutor, Context appContext,
			DuplexPluginCallback callback) {
		return new SimpleSocketPlugin(pluginExecutor, callback,
				POLLING_INTERVAL);
	}
}
