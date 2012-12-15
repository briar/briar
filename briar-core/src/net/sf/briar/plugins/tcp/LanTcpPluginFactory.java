package net.sf.briar.plugins.tcp;

import java.util.concurrent.Executor;

import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexPluginFactory;
import net.sf.briar.api.protocol.TransportId;

public class LanTcpPluginFactory implements DuplexPluginFactory {

	private static final long POLLING_INTERVAL = 60L * 1000L; // 1 minute

	private final Executor pluginExecutor;

	public LanTcpPluginFactory(@PluginExecutor Executor pluginExecutor) {
		this.pluginExecutor = pluginExecutor;
	}

	public TransportId getId() {
		return LanTcpPlugin.ID;
	}

	public DuplexPlugin createPlugin(DuplexPluginCallback callback) {
		return new LanTcpPlugin(pluginExecutor, callback, POLLING_INTERVAL);
	}
}
