package org.briarproject.bramble.plugin.bluetooth;

import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.io.TimeoutMonitor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.BackoffFactory;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginFactory;

import java.security.SecureRandom;
import java.util.concurrent.Executor;

import javax.annotation.concurrent.Immutable;

import static org.briarproject.bramble.api.plugin.BluetoothConstants.ID;

@Immutable
@NotNullByDefault
public class JavaBluetoothPluginFactory implements DuplexPluginFactory {

	private static final int MAX_LATENCY = 30 * 1000; // 30 seconds
	private static final int MAX_IDLE_TIME = 30 * 1000; // 30 seconds
	private static final int MIN_POLLING_INTERVAL = 60 * 1000; // 1 minute
	private static final int MAX_POLLING_INTERVAL = 10 * 60 * 1000; // 10 mins
	private static final double BACKOFF_BASE = 1.2;

	private final Executor ioExecutor;
	private final SecureRandom secureRandom;
	private final EventBus eventBus;
	private final TimeoutMonitor timeoutMonitor;
	private final BackoffFactory backoffFactory;

	public JavaBluetoothPluginFactory(Executor ioExecutor,
			SecureRandom secureRandom, EventBus eventBus,
			TimeoutMonitor timeoutMonitor, BackoffFactory backoffFactory) {
		this.ioExecutor = ioExecutor;
		this.secureRandom = secureRandom;
		this.eventBus = eventBus;
		this.timeoutMonitor = timeoutMonitor;
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
		BluetoothConnectionLimiter connectionLimiter =
				new BluetoothConnectionLimiterImpl(eventBus);
		Backoff backoff = backoffFactory.createBackoff(MIN_POLLING_INTERVAL,
				MAX_POLLING_INTERVAL, BACKOFF_BASE);
		JavaBluetoothPlugin plugin = new JavaBluetoothPlugin(connectionLimiter,
				timeoutMonitor, ioExecutor, secureRandom, backoff, callback,
				MAX_LATENCY, MAX_IDLE_TIME);
		eventBus.addListener(plugin);
		return plugin;
	}
}
