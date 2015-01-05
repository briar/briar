package org.briarproject.plugins.tcp;

import java.util.concurrent.Executor;

import org.briarproject.api.TransportId;
import org.briarproject.api.lifecycle.ShutdownManager;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.plugins.duplex.DuplexPluginFactory;

public class WanTcpPluginFactory implements DuplexPluginFactory {

	private static final int MAX_LATENCY = 30 * 1000; // 30 seconds
	private static final int MAX_IDLE_TIME = 30 * 1000; // 30 seconds
	private static final int POLLING_INTERVAL = 5 * 60 * 1000; // 5 minutes

	private final Executor ioExecutor;
	private final ShutdownManager shutdownManager;

	public WanTcpPluginFactory(Executor ioExecutor,
			ShutdownManager shutdownManager) {
		this.ioExecutor = ioExecutor;
		this.shutdownManager = shutdownManager;
	}

	public TransportId getId() {
		return WanTcpPlugin.ID;
	}

	public DuplexPlugin createPlugin(DuplexPluginCallback callback) {
		return new WanTcpPlugin(ioExecutor, new PortMapperImpl(shutdownManager),
				callback, MAX_LATENCY, MAX_IDLE_TIME, POLLING_INTERVAL);
	}
}
