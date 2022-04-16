package org.briarproject.bramble.plugin.tor;

import org.briarproject.bramble.api.battery.BatteryManager;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.network.NetworkManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.plugin.TorControlPort;
import org.briarproject.bramble.api.plugin.TorDirectory;
import org.briarproject.bramble.api.plugin.TorSocksPort;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.LocationUtils;
import org.briarproject.bramble.api.system.ResourceProvider;
import org.briarproject.bramble.api.system.WakefulIoExecutor;

import java.io.File;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;
import javax.net.SocketFactory;

import static java.util.logging.Level.INFO;
import static org.briarproject.bramble.util.OsUtils.isLinux;

@Immutable
@NotNullByDefault
public class UnixTorPluginFactory extends TorPluginFactory {

	@Inject
	UnixTorPluginFactory(@IoExecutor Executor ioExecutor,
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
		super(ioExecutor, wakefulIoExecutor, networkManager, locationUtils,
				eventBus, torSocketFactory, resourceProvider,
				circumventionProvider, batteryManager, clock, crypto,
				torDirectory, torSocksPort, torControlPort);
	}

	@Nullable
	@Override
	String getArchitectureForTorBinary() {
		if (!isLinux()) return null;
		String arch = System.getProperty("os.arch");
		if (LOG.isLoggable(INFO)) {
			LOG.info("System's os.arch is " + arch);
		}
		if (arch.equals("amd64")) return "linux-x86_64";
		else if (arch.equals("aarch64")) return "linux-aarch64";
		else if (arch.equals("arm")) return "linux-armhf";
		return null;
	}

	@Override
	TorPlugin createPluginInstance(TorRendezvousCrypto torRendezvousCrypto,
			PluginCallback callback,
			String architecture) {
		return new UnixTorPlugin(ioExecutor, wakefulIoExecutor,
				networkManager, locationUtils, torSocketFactory, clock,
				resourceProvider, circumventionProvider, batteryManager,
				torRendezvousCrypto, callback, architecture,
				MAX_LATENCY, MAX_IDLE_TIME, torDirectory, torSocksPort,
				torControlPort);
	}
}
