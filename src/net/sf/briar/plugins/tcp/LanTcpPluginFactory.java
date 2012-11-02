package net.sf.briar.plugins.tcp;

import java.util.concurrent.Executor;

import net.sf.briar.api.android.AndroidExecutor;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexPluginFactory;
import android.content.Context;

public class LanTcpPluginFactory implements DuplexPluginFactory {

	private static final long POLLING_INTERVAL = 60L * 1000L; // 1 minute

	public DuplexPlugin createPlugin(@PluginExecutor Executor pluginExecutor,
			AndroidExecutor androidExecutor, Context appContext,
			DuplexPluginCallback callback) {
		return new LanTcpPlugin(pluginExecutor, callback, POLLING_INTERVAL);
	}
}
