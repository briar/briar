package net.sf.briar.plugins.tcp;

import java.util.concurrent.Executor;

import net.sf.briar.api.lifecycle.ShutdownManager;
import net.sf.briar.api.messaging.TransportId;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexPluginFactory;

public class WanTcpPluginFactory implements DuplexPluginFactory {

	private static final long POLLING_INTERVAL = 5L * 60L * 1000L; // 5 minutes

	private final Executor pluginExecutor;
	private final ShutdownManager shutdownManager;

	public WanTcpPluginFactory(@PluginExecutor Executor pluginExecutor,
			ShutdownManager shutdownManager) {
		this.pluginExecutor = pluginExecutor;
		this.shutdownManager = shutdownManager;
	}

	public TransportId getId() {
		return WanTcpPlugin.ID;
	}

	public DuplexPlugin createPlugin(DuplexPluginCallback callback) {
		return new WanTcpPlugin(pluginExecutor, callback, POLLING_INTERVAL,
				new PortMapperImpl(shutdownManager));
	}
}
