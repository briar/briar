package org.briarproject.bramble.plugin.bluetooth;

import android.app.Application;
import android.bluetooth.BluetoothSocket;

import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.io.TimeoutMonitor;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.BackoffFactory;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginFactory;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.bramble.api.system.AndroidWakeLockManager;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.WakefulIoExecutor;

import java.security.SecureRandom;
import java.util.concurrent.Executor;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.bramble.api.plugin.BluetoothConstants.ID;

@Immutable
@NotNullByDefault
public class AndroidBluetoothPluginFactory implements DuplexPluginFactory {

	private static final int MAX_LATENCY = 30 * 1000; // 30 seconds
	private static final int MAX_IDLE_TIME = 30 * 1000; // 30 seconds
	private static final int MIN_POLLING_INTERVAL = 60 * 1000; // 1 minute
	private static final int MAX_POLLING_INTERVAL = 10 * 60 * 1000; // 10 mins
	private static final double BACKOFF_BASE = 1.2;

	private final Executor ioExecutor, wakefulIoExecutor;
	private final AndroidExecutor androidExecutor;
	private final AndroidWakeLockManager wakeLockManager;
	private final Application app;
	private final SecureRandom secureRandom;
	private final EventBus eventBus;
	private final Clock clock;
	private final TimeoutMonitor timeoutMonitor;
	private final BackoffFactory backoffFactory;

	@Inject
	AndroidBluetoothPluginFactory(@IoExecutor Executor ioExecutor,
			@WakefulIoExecutor Executor wakefulIoExecutor,
			AndroidExecutor androidExecutor,
			AndroidWakeLockManager wakeLockManager,
			Application app,
			SecureRandom secureRandom,
			EventBus eventBus,
			Clock clock,
			TimeoutMonitor timeoutMonitor,
			BackoffFactory backoffFactory) {
		this.ioExecutor = ioExecutor;
		this.wakefulIoExecutor = wakefulIoExecutor;
		this.androidExecutor = androidExecutor;
		this.wakeLockManager = wakeLockManager;
		this.app = app;
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
	public long getMaxLatency() {
		return MAX_LATENCY;
	}

	@Override
	public DuplexPlugin createPlugin(PluginCallback callback) {
		BluetoothConnectionLimiter connectionLimiter =
				new BluetoothConnectionLimiterImpl(eventBus);
		BluetoothConnectionFactory<BluetoothSocket> connectionFactory =
				new AndroidBluetoothConnectionFactory(connectionLimiter,
						wakeLockManager, timeoutMonitor);
		Backoff backoff = backoffFactory.createBackoff(MIN_POLLING_INTERVAL,
				MAX_POLLING_INTERVAL, BACKOFF_BASE);
		AndroidBluetoothPlugin plugin = new AndroidBluetoothPlugin(
				connectionLimiter, connectionFactory, ioExecutor,
				wakefulIoExecutor, secureRandom, androidExecutor, app,
				clock, backoff, callback, MAX_LATENCY, MAX_IDLE_TIME);
		eventBus.addListener(plugin);
		return plugin;
	}
}
