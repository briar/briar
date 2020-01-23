package org.briarproject.bramble.plugin.bluetooth;

import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.io.TimeoutMonitor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginFactory;

import java.security.SecureRandom;
import java.util.concurrent.Executor;

import javax.annotation.concurrent.Immutable;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.ID;

@Immutable
@NotNullByDefault
public class JavaBluetoothPluginFactory implements DuplexPluginFactory {

	private static final int MAX_LATENCY = (int) SECONDS.toMillis(30);
	private static final int MAX_IDLE_TIME = (int) SECONDS.toMillis(30);
	private static final int POLLING_INTERVAL = (int) MINUTES.toMillis(2);

	private final Executor ioExecutor;
	private final SecureRandom secureRandom;
	private final EventBus eventBus;
	private final TimeoutMonitor timeoutMonitor;

	public JavaBluetoothPluginFactory(Executor ioExecutor,
			SecureRandom secureRandom, EventBus eventBus,
			TimeoutMonitor timeoutMonitor) {
		this.ioExecutor = ioExecutor;
		this.secureRandom = secureRandom;
		this.eventBus = eventBus;
		this.timeoutMonitor = timeoutMonitor;
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
		JavaBluetoothPlugin plugin = new JavaBluetoothPlugin(connectionLimiter,
				timeoutMonitor, ioExecutor, secureRandom, callback, MAX_LATENCY,
				MAX_IDLE_TIME, POLLING_INTERVAL);
		eventBus.addListener(plugin);
		return plugin;
	}
}
