package org.briarproject.bramble.plugin.tor;

import org.briarproject.bramble.api.battery.BatteryManager;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.network.NetworkManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.plugin.TorConstants;
import org.briarproject.bramble.api.plugin.TorControlPort;
import org.briarproject.bramble.api.plugin.TorDirectory;
import org.briarproject.bramble.api.plugin.TorSocksPort;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginFactory;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.LocationUtils;
import org.briarproject.bramble.api.system.ResourceProvider;
import org.briarproject.bramble.api.system.WakefulIoExecutor;

import java.io.File;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.net.SocketFactory;

import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;

@Immutable
@NotNullByDefault
abstract class TorPluginFactory implements DuplexPluginFactory {

	protected static final Logger LOG =
			getLogger(TorPluginFactory.class.getName());

	protected static final int MAX_LATENCY = 30 * 1000; // 30 seconds
	protected static final int MAX_IDLE_TIME = 30 * 1000; // 30 seconds

	protected final Executor ioExecutor, wakefulIoExecutor;
	protected final NetworkManager networkManager;
	protected final LocationUtils locationUtils;
	protected final EventBus eventBus;
	protected final SocketFactory torSocketFactory;
	protected final ResourceProvider resourceProvider;
	protected final CircumventionProvider circumventionProvider;
	protected final BatteryManager batteryManager;
	protected final Clock clock;
	protected final CryptoComponent crypto;
	protected final File torDirectory;
	protected final int torSocksPort;
	protected final int torControlPort;

	TorPluginFactory(@IoExecutor Executor ioExecutor,
			@WakefulIoExecutor Executor wakefulIoExecutor,
			NetworkManager networkManager,
			LocationUtils locationUtils,
			EventBus eventBus,
			SocketFactory torSocketFactory,
			ResourceProvider resourceProvider,
			CircumventionProvider circumventionProvider,
			BatteryManager batteryManager,
			Clock clock,
			CryptoComponent crypto,
			@TorDirectory File torDirectory,
			@TorSocksPort int torSocksPort,
			@TorControlPort int torControlPort) {
		this.ioExecutor = ioExecutor;
		this.wakefulIoExecutor = wakefulIoExecutor;
		this.networkManager = networkManager;
		this.locationUtils = locationUtils;
		this.eventBus = eventBus;
		this.torSocketFactory = torSocketFactory;
		this.resourceProvider = resourceProvider;
		this.circumventionProvider = circumventionProvider;
		this.batteryManager = batteryManager;
		this.clock = clock;
		this.crypto = crypto;
		this.torDirectory = torDirectory;
		this.torSocksPort = torSocksPort;
		this.torControlPort = torControlPort;
	}

	@Nullable
	abstract String getArchitectureForTorBinary();

	abstract TorPlugin createPluginInstance(
			TorRendezvousCrypto torRendezvousCrypto, PluginCallback callback,
			String architecture);

	@Override
	public TransportId getId() {
		return TorConstants.ID;
	}

	@Override
	public long getMaxLatency() {
		return MAX_LATENCY;
	}

	@Override
	public DuplexPlugin createPlugin(PluginCallback callback) {
		// Check that we have a Tor binary for this architecture
		String architecture = getArchitectureForTorBinary();
		if (architecture == null) {
			LOG.warning("Tor is not supported on this architecture");
			return null;
		}

		if (LOG.isLoggable(INFO)) {
			LOG.info("The selected architecture for Tor is " + architecture);
		}

		TorRendezvousCrypto torRendezvousCrypto =
				new TorRendezvousCryptoImpl(crypto);
		TorPlugin plugin = createPluginInstance(torRendezvousCrypto,
				callback, architecture);
		eventBus.addListener(plugin);
		return plugin;
	}
}
