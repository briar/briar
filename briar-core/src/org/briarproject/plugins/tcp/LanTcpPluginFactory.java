package org.briarproject.plugins.tcp;

import org.briarproject.api.TransportId;
import org.briarproject.api.plugins.Backoff;
import org.briarproject.api.plugins.BackoffFactory;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.plugins.duplex.DuplexPluginFactory;

import java.util.concurrent.Executor;

public class LanTcpPluginFactory implements DuplexPluginFactory {

	private static final int MAX_LATENCY = 30 * 1000; // 30 seconds
	private static final int MAX_IDLE_TIME = 30 * 1000; // 30 seconds
	private static final int MIN_POLLING_INTERVAL = 2 * 60 * 1000; // 2 minutes
	private static final int MAX_POLLING_INTERVAL = 60 * 60 * 1000; // 1 hour
	private static final double BACKOFF_BASE = 1.2;

	private final Executor ioExecutor;
	private final BackoffFactory backoffFactory;

	public LanTcpPluginFactory(Executor ioExecutor,
			BackoffFactory backoffFactory) {
		this.ioExecutor = ioExecutor;
		this.backoffFactory = backoffFactory;
	}

	public TransportId getId() {
		return LanTcpPlugin.ID;
	}

	public int getMaxLatency() {
		return MAX_LATENCY;
	}

	public DuplexPlugin createPlugin(DuplexPluginCallback callback) {
		Backoff backoff = backoffFactory.createBackoff(MIN_POLLING_INTERVAL,
				MAX_POLLING_INTERVAL, BACKOFF_BASE);
		return new LanTcpPlugin(ioExecutor, backoff, callback, MAX_LATENCY,
				MAX_IDLE_TIME);
	}
}
