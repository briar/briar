package org.briarproject.bramble.plugin.tcp;

import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.BackoffFactory;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginFactory;

import java.util.concurrent.Executor;

import javax.annotation.concurrent.Immutable;

import static org.briarproject.bramble.api.plugin.LanTcpConstants.ID;

@Immutable
@NotNullByDefault
public class LanTcpPluginFactory implements DuplexPluginFactory {

	private static final int MAX_LATENCY = 30_000; // 30 seconds
	private static final int MAX_IDLE_TIME = 30_000; // 30 seconds
	private static final int CONNECTION_TIMEOUT = 3_000; // 3 seconds
	private static final int MIN_POLLING_INTERVAL = 60_000; // 1 minute
	private static final int MAX_POLLING_INTERVAL = 600_000; // 10 mins
	private static final double BACKOFF_BASE = 1.2;

	private final Executor ioExecutor;
	private final EventBus eventBus;
	private final BackoffFactory backoffFactory;

	public LanTcpPluginFactory(Executor ioExecutor, EventBus eventBus,
			BackoffFactory backoffFactory) {
		this.ioExecutor = ioExecutor;
		this.eventBus = eventBus;
		this.backoffFactory = backoffFactory;
	}

	@Override
	public TransportId getId() {
		return ID;
	}

	@Override
	public int getMaxLatency() {
		return MAX_LATENCY;
	}

	@Override
	public DuplexPlugin createPlugin(PluginCallback callback) {
		Backoff backoff = backoffFactory.createBackoff(MIN_POLLING_INTERVAL,
				MAX_POLLING_INTERVAL, BACKOFF_BASE);
		LanTcpPlugin plugin =  new LanTcpPlugin(ioExecutor, backoff, callback, MAX_LATENCY,
				MAX_IDLE_TIME, CONNECTION_TIMEOUT);
		eventBus.addListener(plugin);
		return plugin;
	}
}
