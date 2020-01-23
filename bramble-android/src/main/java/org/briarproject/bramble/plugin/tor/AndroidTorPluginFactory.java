package org.briarproject.bramble.plugin.tor;

import android.content.Context;

import org.briarproject.bramble.api.battery.BatteryManager;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.network.NetworkManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.plugin.TorConstants;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginFactory;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.LocationUtils;
import org.briarproject.bramble.api.system.ResourceProvider;
import org.briarproject.bramble.util.AndroidUtils;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

import javax.annotation.concurrent.Immutable;
import javax.net.SocketFactory;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Logger.getLogger;

@Immutable
@NotNullByDefault
public class AndroidTorPluginFactory implements DuplexPluginFactory {

	private static final Logger LOG =
			getLogger(AndroidTorPluginFactory.class.getName());

	private static final int MAX_LATENCY = (int) SECONDS.toMillis(30);
	private static final int MAX_IDLE_TIME = (int) SECONDS.toMillis(30);

	/**
	 * How often to poll before our hidden service becomes reachable.
	 */
	private static final int INITIAL_POLLING_INTERVAL =
			(int) MINUTES.toMillis(1);

	/**
	 * How often to poll when our hidden service is reachable. Our contacts
	 * will poll when they come online, so our polling is just a fallback in
	 * case of repeated connection failures.
	 */
	private static final int STABLE_POLLING_INTERVAL =
			(int) MINUTES.toMillis(15);

	private final Executor ioExecutor;
	private final ScheduledExecutorService scheduler;
	private final Context appContext;
	private final NetworkManager networkManager;
	private final LocationUtils locationUtils;
	private final EventBus eventBus;
	private final SocketFactory torSocketFactory;
	private final ResourceProvider resourceProvider;
	private final CircumventionProvider circumventionProvider;
	private final BatteryManager batteryManager;
	private final Clock clock;

	public AndroidTorPluginFactory(Executor ioExecutor,
			ScheduledExecutorService scheduler, Context appContext,
			NetworkManager networkManager, LocationUtils locationUtils,
			EventBus eventBus, SocketFactory torSocketFactory,
			ResourceProvider resourceProvider,
			CircumventionProvider circumventionProvider,
			BatteryManager batteryManager, Clock clock) {
		this.ioExecutor = ioExecutor;
		this.scheduler = scheduler;
		this.appContext = appContext;
		this.networkManager = networkManager;
		this.locationUtils = locationUtils;
		this.eventBus = eventBus;
		this.torSocketFactory = torSocketFactory;
		this.resourceProvider = resourceProvider;
		this.circumventionProvider = circumventionProvider;
		this.batteryManager = batteryManager;
		this.clock = clock;
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
		for (String abi : AndroidUtils.getSupportedArchitectures()) {
			if (abi.startsWith("x86_64")) {
				architecture = "x86_64";
				break;
			} else if (abi.startsWith("x86")) {
				architecture = "x86";
				break;
			} else if (abi.startsWith("arm64")) {
				architecture = "arm64";
				break;
			} else if (abi.startsWith("armeabi")) {
				architecture = "arm";
				break;
			}
		}
		if (architecture == null) {
			LOG.info("Tor is not supported on this architecture");
			return null;
		}
		// Use position-independent executable
		architecture += "_pie";

		TorRendezvousCrypto torRendezvousCrypto = new TorRendezvousCryptoImpl();
		AndroidTorPlugin plugin = new AndroidTorPlugin(ioExecutor, scheduler,
				appContext, networkManager, locationUtils, torSocketFactory,
				clock, resourceProvider, circumventionProvider, batteryManager,
				torRendezvousCrypto, callback, architecture, MAX_LATENCY,
				MAX_IDLE_TIME, INITIAL_POLLING_INTERVAL,
				STABLE_POLLING_INTERVAL);
		eventBus.addListener(plugin);
		return plugin;
	}
}
