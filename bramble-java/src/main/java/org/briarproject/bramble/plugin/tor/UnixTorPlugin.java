package org.briarproject.bramble.plugin.tor;

import com.sun.jna.Library;
import com.sun.jna.Native;

import org.briarproject.bramble.api.battery.BatteryManager;
import org.briarproject.bramble.api.network.NetworkManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.LocationUtils;
import org.briarproject.bramble.api.system.ResourceProvider;

import java.io.File;
import java.util.concurrent.Executor;

import javax.net.SocketFactory;

@NotNullByDefault
class UnixTorPlugin extends JavaTorPlugin {

	UnixTorPlugin(Executor ioExecutor,
			Executor wakefulIoExecutor,
			NetworkManager networkManager,
			LocationUtils locationUtils,
			SocketFactory torSocketFactory,
			Clock clock,
			ResourceProvider resourceProvider,
			CircumventionProvider circumventionProvider,
			BatteryManager batteryManager,
			Backoff backoff,
			TorRendezvousCrypto torRendezvousCrypto,
			PluginCallback callback,
			String architecture,
			long maxLatency,
			int maxIdleTime,
			File torDirectory,
			int torSocksPort,
			int torControlPort) {
		super(ioExecutor, wakefulIoExecutor, networkManager, locationUtils,
				torSocketFactory, clock, resourceProvider,
				circumventionProvider, batteryManager, backoff,
				torRendezvousCrypto, callback, architecture,
				maxLatency, maxIdleTime, torDirectory, torSocksPort,
				torControlPort);
	}

	@Override
	protected int getProcessId() {
		return CLibrary.INSTANCE.getpid();
	}

	private interface CLibrary extends Library {

		CLibrary INSTANCE = Native.loadLibrary("c", CLibrary.class);

		int getpid();
	}
}
