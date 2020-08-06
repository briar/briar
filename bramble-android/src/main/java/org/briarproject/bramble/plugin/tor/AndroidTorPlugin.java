package org.briarproject.bramble.plugin.tor;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

import org.briarproject.bramble.api.battery.BatteryManager;
import org.briarproject.bramble.api.network.NetworkManager;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.system.AndroidWakeLock;
import org.briarproject.bramble.api.system.AndroidWakeLockManager;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.LocationUtils;
import org.briarproject.bramble.api.system.ResourceProvider;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executor;

import javax.net.SocketFactory;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class AndroidTorPlugin extends TorPlugin {

	private final Application app;
	private final AndroidWakeLock wakeLock;

	AndroidTorPlugin(Executor ioExecutor,
			Executor wakefulIoExecutor,
			Application app,
			NetworkManager networkManager,
			LocationUtils locationUtils,
			SocketFactory torSocketFactory,
			Clock clock,
			ResourceProvider resourceProvider,
			CircumventionProvider circumventionProvider,
			BatteryManager batteryManager,
			AndroidWakeLockManager wakeLockManager,
			Backoff backoff,
			TorRendezvousCrypto torRendezvousCrypto,
			PluginCallback callback,
			String architecture,
			int maxLatency,
			int maxIdleTime,
			File torDirectory) {
		super(ioExecutor, wakefulIoExecutor, networkManager, locationUtils,
				torSocketFactory, clock, resourceProvider,
				circumventionProvider, batteryManager, backoff,
				torRendezvousCrypto, callback, architecture, maxLatency,
				maxIdleTime, torDirectory);
		this.app = app;
		wakeLock = wakeLockManager.createWakeLock("TorPlugin");
	}

	@Override
	protected int getProcessId() {
		return android.os.Process.myPid();
	}

	@Override
	protected long getLastUpdateTime() {
		try {
			PackageManager pm = app.getPackageManager();
			PackageInfo pi = pm.getPackageInfo(app.getPackageName(), 0);
			return pi.lastUpdateTime;
		} catch (NameNotFoundException e) {
			throw new AssertionError(e);
		}
	}

	@Override
	protected void enableNetwork(boolean enable) throws IOException {
		if (enable) wakeLock.acquire();
		super.enableNetwork(enable);
		if (!enable) wakeLock.release();
	}

	@Override
	public void stop() {
		super.stop();
		wakeLock.release();
	}
}
