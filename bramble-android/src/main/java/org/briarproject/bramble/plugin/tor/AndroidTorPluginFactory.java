package org.briarproject.bramble.plugin.tor;

import android.app.Application;

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
import org.briarproject.bramble.api.system.AndroidWakeLockManager;
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

import static org.briarproject.bramble.util.AndroidUtils.getSupportedArchitectures;

@Immutable
@NotNullByDefault
public class AndroidTorPluginFactory extends TorPluginFactory {

	private final Application app;
	private final AndroidWakeLockManager wakeLockManager;

	@Inject
	AndroidTorPluginFactory(@IoExecutor Executor ioExecutor,
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
			@TorControlPort int torControlPort,
			Application app,
			AndroidWakeLockManager wakeLockManager) {
		super(ioExecutor, wakefulIoExecutor, networkManager, locationUtils,
				eventBus, torSocketFactory, resourceProvider,
				circumventionProvider, batteryManager, clock, crypto,
				torDirectory, torSocksPort, torControlPort);
		this.app = app;
		this.wakeLockManager = wakeLockManager;
	}

	@Nullable
	@Override
	String getArchitectureForTorBinary() {
		for (String abi : getSupportedArchitectures()) {
			if (abi.startsWith("x86_64")) return "x86_64_pie";
			else if (abi.startsWith("x86")) return "x86_pie";
			else if (abi.startsWith("arm64")) return "arm64_pie";
			else if (abi.startsWith("armeabi")) return "arm_pie";
		}
		return null;
	}

	@Override
	TorPlugin createPluginInstance(TorRendezvousCrypto torRendezvousCrypto,
			PluginCallback callback,
			String architecture) {
		return new AndroidTorPlugin(ioExecutor,
				wakefulIoExecutor, app, networkManager, locationUtils,
				torSocketFactory, clock, resourceProvider,
				circumventionProvider, batteryManager, wakeLockManager,
				torRendezvousCrypto, callback, architecture,
				MAX_LATENCY, MAX_IDLE_TIME, torDirectory, torSocksPort,
				torControlPort);
	}
}
