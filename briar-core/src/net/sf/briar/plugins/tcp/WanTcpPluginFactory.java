package net.sf.briar.plugins.tcp;

import java.util.concurrent.Executor;

import net.sf.briar.api.TransportId;
import net.sf.briar.api.lifecycle.ShutdownManager;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexPluginFactory;

public class WanTcpPluginFactory implements DuplexPluginFactory {

	private static final int MAX_FRAME_LENGTH = 1024;
	private static final long MAX_LATENCY = 60 * 1000; // 1 minute
	private static final long POLLING_INTERVAL = 5 * 60 * 1000; // 5 minutes

	private final Executor pluginExecutor;
	private final ShutdownManager shutdownManager;

	public WanTcpPluginFactory(Executor pluginExecutor,
			ShutdownManager shutdownManager) {
		this.pluginExecutor = pluginExecutor;
		this.shutdownManager = shutdownManager;
	}

	public TransportId getId() {
		return WanTcpPlugin.ID;
	}

	public DuplexPlugin createPlugin(DuplexPluginCallback callback) {
		return new WanTcpPlugin(pluginExecutor, callback, MAX_FRAME_LENGTH,
				MAX_LATENCY, POLLING_INTERVAL,
				new PortMapperImpl(shutdownManager));
	}
}
