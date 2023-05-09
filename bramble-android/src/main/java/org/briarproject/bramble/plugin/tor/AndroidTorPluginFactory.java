package org.briarproject.bramble.plugin.tor;

import android.app.Application;

import org.briarproject.android.dontkillmelib.wakelock.AndroidWakeLockManager;
import org.briarproject.bramble.api.battery.BatteryManager;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventExecutor;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.network.NetworkManager;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.BackoffFactory;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.plugin.TorControlPort;
import org.briarproject.bramble.api.plugin.TorDirectory;
import org.briarproject.bramble.api.plugin.TorSocksPort;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.WakefulIoExecutor;
import org.briarproject.nullsafety.NotNullByDefault;
import org.briarproject.onionwrapper.AndroidTorWrapper;
import org.briarproject.onionwrapper.CircumventionProvider;
import org.briarproject.onionwrapper.LocationUtils;
import org.briarproject.onionwrapper.TorWrapper;

import java.io.File;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;
import javax.net.SocketFactory;

import static android.os.Build.VERSION.SDK_INT;
import static org.briarproject.bramble.util.AndroidUtils.getSupportedArchitectures;

@Immutable
@NotNullByDefault
public class AndroidTorPluginFactory extends TorPluginFactory {

	private final Application app;
	private final AndroidWakeLockManager wakeLockManager;

	@Inject
	AndroidTorPluginFactory(@IoExecutor Executor ioExecutor,
			@EventExecutor Executor eventExecutor,
			@WakefulIoExecutor Executor wakefulIoExecutor,
			NetworkManager networkManager,
			LocationUtils locationUtils,
			EventBus eventBus,
			SocketFactory torSocketFactory,
			BackoffFactory backoffFactory,
			CircumventionProvider circumventionProvider,
			BatteryManager batteryManager,
			Clock clock,
			CryptoComponent crypto,
			@TorDirectory File torDirectory,
			@TorSocksPort int torSocksPort,
			@TorControlPort int torControlPort,
			Application app,
			AndroidWakeLockManager wakeLockManager) {
		super(ioExecutor, eventExecutor, wakefulIoExecutor, networkManager,
				locationUtils, eventBus, torSocketFactory, backoffFactory,
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
	TorPlugin createPluginInstance(Backoff backoff,
			TorRendezvousCrypto torRendezvousCrypto, PluginCallback callback,
			String architecture) {
		TorWrapper tor = new AndroidTorWrapper(app, wakeLockManager,
				ioExecutor, eventExecutor, architecture, torDirectory,
				torSocksPort, torControlPort);
		// Android versions 7.1 and newer can verify Let's Encrypt TLS certs
		// signed with the IdentTrust DST Root X3 certificate. Older versions
		// of Android consider the certificate to have expired at the end of
		// September 2021.
		boolean canVerifyLetsEncryptCerts = SDK_INT >= 25;
		return new TorPlugin(ioExecutor, wakefulIoExecutor,
				networkManager, locationUtils, torSocketFactory,
				circumventionProvider, batteryManager, backoff,
				torRendezvousCrypto, tor, callback, MAX_LATENCY,
				MAX_IDLE_TIME, canVerifyLetsEncryptCerts);
	}
}
