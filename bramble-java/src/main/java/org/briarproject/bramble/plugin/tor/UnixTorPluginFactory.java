package org.briarproject.bramble.plugin.tor;

import org.briarproject.bramble.api.battery.BatteryManager;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.network.NetworkManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.BackoffFactory;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.plugin.TorConstants;
import org.briarproject.bramble.api.plugin.TorDirectory;
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

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;
import javax.net.SocketFactory;

import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.OsUtils.isLinux;

@Immutable
@NotNullByDefault
public class UnixTorPluginFactory implements DuplexPluginFactory {

	private static final Logger LOG =
			getLogger(UnixTorPluginFactory.class.getName());

	private static final int MAX_LATENCY = 30 * 1000; // 30 seconds
	private static final int MAX_IDLE_TIME = 30 * 1000; // 30 seconds
	private static final int MIN_POLLING_INTERVAL = 60 * 1000; // 1 minute
	private static final int MAX_POLLING_INTERVAL = 10 * 60 * 1000; // 10 mins
	private static final double BACKOFF_BASE = 1.2;

	private final Executor ioExecutor, wakefulIoExecutor;
	private final NetworkManager networkManager;
	private final LocationUtils locationUtils;
	private final EventBus eventBus;
	private final SocketFactory torSocketFactory;
	private final BackoffFactory backoffFactory;
	private final ResourceProvider resourceProvider;
	private final CircumventionProvider circumventionProvider;
	private final BatteryManager batteryManager;
	private final Clock clock;
	private final File torDirectory;

	@Inject
	UnixTorPluginFactory(@IoExecutor Executor ioExecutor,
			@WakefulIoExecutor Executor wakefulIoExecutor,
			NetworkManager networkManager,
			LocationUtils locationUtils,
			EventBus eventBus,
			SocketFactory torSocketFactory,
			BackoffFactory backoffFactory,
			ResourceProvider resourceProvider,
			CircumventionProvider circumventionProvider,
			BatteryManager batteryManager,
			Clock clock,
			@TorDirectory File torDirectory) {
		this.ioExecutor = ioExecutor;
		this.wakefulIoExecutor = wakefulIoExecutor;
		this.networkManager = networkManager;
		this.locationUtils = locationUtils;
		this.eventBus = eventBus;
		this.torSocketFactory = torSocketFactory;
		this.backoffFactory = backoffFactory;
		this.resourceProvider = resourceProvider;
		this.circumventionProvider = circumventionProvider;
		this.batteryManager = batteryManager;
		this.clock = clock;
		this.torDirectory = torDirectory;
	}

	@Override
	public TransportId getId() {
		return TorConstants.ID;
	}

	@Override
	public int getMaxLatency() {
		return MAX_LATENCY;
	}

	@Override
	public DuplexPlugin createPlugin(PluginCallback callback) {
		// Check that we have a Tor binary for this architecture
		String architecture = null;
		if (isLinux()) {
			String arch = System.getProperty("os.arch");
			if (LOG.isLoggable(INFO)) {
				LOG.info("System's os.arch is " + arch);
			}
			if (arch.equals("amd64")) {
				architecture = "linux-x86_64";
			} else if (arch.equals("aarch64")) {
				architecture = "linux-aarch64";
			} else if (arch.equals("arm")) {
				architecture = "linux-armhf";
			}
		}
		if (architecture == null) {
			LOG.info("Tor is not supported on this architecture");
			return null;
		}

		if (LOG.isLoggable(INFO)) {
			LOG.info("The selected architecture for Tor is " + architecture);
		}

		Backoff backoff = backoffFactory.createBackoff(MIN_POLLING_INTERVAL,
				MAX_POLLING_INTERVAL, BACKOFF_BASE);
		TorRendezvousCrypto torRendezvousCrypto = new TorRendezvousCryptoImpl();
		UnixTorPlugin plugin = new UnixTorPlugin(ioExecutor, wakefulIoExecutor,
				networkManager, locationUtils, torSocketFactory, clock,
				resourceProvider, circumventionProvider, batteryManager,
				backoff, torRendezvousCrypto, callback, architecture,
				MAX_LATENCY, MAX_IDLE_TIME, torDirectory);
		eventBus.addListener(plugin);
		return plugin;
	}
}
