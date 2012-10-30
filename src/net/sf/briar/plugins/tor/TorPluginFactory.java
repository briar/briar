package net.sf.briar.plugins.tor;

import java.util.concurrent.Executor;

import net.sf.briar.api.android.AndroidExecutor;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexPluginFactory;
import android.content.Context;

public class TorPluginFactory implements DuplexPluginFactory {

	private static final long POLLING_INTERVAL = 15L * 60L * 1000L; // 15 mins

	public DuplexPlugin createPlugin(@PluginExecutor Executor pluginExecutor,
			AndroidExecutor androidExecutor, Context appContext,
			DuplexPluginCallback callback) {
		return new TorPlugin(pluginExecutor, callback, POLLING_INTERVAL);
	}
}
