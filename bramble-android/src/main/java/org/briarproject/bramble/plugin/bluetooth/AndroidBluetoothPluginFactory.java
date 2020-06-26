package org.briarproject.bramble.plugin.bluetooth;

import android.content.Context;

import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.io.TimeoutMonitor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.BackoffFactory;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginFactory;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.bramble.api.system.Clock;

import java.security.SecureRandom;
import java.util.concurrent.Executor;

import javax.annotation.concurrent.Immutable;

import static org.briarproject.bramble.api.plugin.BluetoothConstants.ID;

@Immutable
@NotNullByDefault
public class AndroidBluetoothPluginFactory implements DuplexPluginFactory {

	private static final int MAX_LATENCY = 30 * 1000; // 30 seconds
	private static final int MAX_IDLE_TIME = 30 * 1000; // 30 seconds
	private static final int MIN_POLLING_INTERVAL = 60 * 1000; // 1 minute
	private static final int MAX_POLLING_INTERVAL = 10 * 60 * 1000; // 10 mins
	private static final double BACKOFF_BASE = 1.2;

	private final Executor ioExecutor;
	private final AndroidExecutor androidExecutor;
	private final Context appContext;
	private final SecureRandom secureRandom;
	private final EventBus eventBus;
	private final Clock clock;
	private final TimeoutMonitor timeoutMonitor;
	private final BackoffFactory backoffFactory;

	public AndroidBluetoothPluginFactory(Executor ioExecutor,
			AndroidExecutor androidExecutor, Context appContext,
			SecureRandom secureRandom, EventBus eventBus, Clock clock,
			TimeoutMonitor timeoutMonitor, BackoffFactory backoffFactory) {
		this.ioExecutor = ioExecutor;
		this.androidExecutor = androidExecutor;
		this.appContext = appContext;
		this.secureRandom = secureRandom;
		this.eventBus = eventBus;
		this.clock = clock;
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
		AndroidBluetoothPlugin plugin = new AndroidBluetoothPlugin(
				connectionLimiter, timeoutMonitor, ioExecutor, secureRandom,
				androidExecutor, appContext, clock, backoff,
				callback, MAX_LATENCY, MAX_IDLE_TIME);
		eventBus.addListener(plugin);
		return plugin;
	}
}
