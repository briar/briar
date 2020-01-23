package org.briarproject.bramble.plugin.tor;

import org.briarproject.bramble.api.battery.BatteryManager;
import org.briarproject.bramble.api.network.NetworkManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.LocationUtils;
import org.briarproject.bramble.api.system.ResourceProvider;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.util.concurrent.Executor;

import javax.net.SocketFactory;

@NotNullByDefault
abstract class JavaTorPlugin extends TorPlugin {

	JavaTorPlugin(Executor ioExecutor, NetworkManager networkManager,
			LocationUtils locationUtils, SocketFactory torSocketFactory,
			Clock clock, ResourceProvider resourceProvider,
			CircumventionProvider circumventionProvider,
			BatteryManager batteryManager,
			TorRendezvousCrypto torRendezvousCrypto,
			PluginCallback callback, String architecture, int maxLatency,
			int maxIdleTime, int initialPollingInterval,
			int stablePollingInterval, File torDirectory) {
		super(ioExecutor, networkManager, locationUtils, torSocketFactory,
				clock, resourceProvider, circumventionProvider, batteryManager,
				torRendezvousCrypto, callback, architecture, maxLatency,
				maxIdleTime, initialPollingInterval, stablePollingInterval,
				torDirectory);
	}

	@Override
	protected long getLastUpdateTime() {
		CodeSource codeSource =
				getClass().getProtectionDomain().getCodeSource();
		if (codeSource == null) throw new AssertionError("CodeSource null");
		try {
			URI path = codeSource.getLocation().toURI();
			File file = new File(path);
			return file.lastModified();
		} catch (URISyntaxException e) {
			throw new AssertionError(e);
		}
	}
}
