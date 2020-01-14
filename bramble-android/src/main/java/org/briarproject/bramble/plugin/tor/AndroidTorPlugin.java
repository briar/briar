package org.briarproject.bramble.plugin.tor;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.PowerManager;

import org.briarproject.bramble.api.battery.BatteryManager;
import org.briarproject.bramble.api.network.NetworkManager;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.LocationUtils;
import org.briarproject.bramble.api.system.ResourceProvider;
import org.briarproject.bramble.util.RenewableWakeLock;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import javax.net.SocketFactory;

import static android.content.Context.MODE_PRIVATE;
import static android.content.Context.POWER_SERVICE;
import static android.os.PowerManager.PARTIAL_WAKE_LOCK;
import static java.util.concurrent.TimeUnit.MINUTES;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class AndroidTorPlugin extends TorPlugin {

	private final Context appContext;
	private final RenewableWakeLock wakeLock;

	AndroidTorPlugin(Executor ioExecutor, ScheduledExecutorService scheduler,
			Context appContext, NetworkManager networkManager,
			LocationUtils locationUtils, SocketFactory torSocketFactory,
			Clock clock, ResourceProvider resourceProvider,
			CircumventionProvider circumventionProvider,
			BatteryManager batteryManager, Backoff backoff,
			TorRendezvousCrypto torRendezvousCrypto,
			PluginCallback callback, String architecture, int maxLatency,
			int maxIdleTime) {
		super(ioExecutor, networkManager, locationUtils, torSocketFactory,
				clock, resourceProvider, circumventionProvider, batteryManager,
				backoff, torRendezvousCrypto, callback, architecture, maxLatency, maxIdleTime,
				appContext.getDir("tor", MODE_PRIVATE));
		this.appContext = appContext;
		PowerManager pm = (PowerManager)
				appContext.getSystemService(POWER_SERVICE);
		if (pm == null) throw new AssertionError();
		wakeLock = new RenewableWakeLock(pm, scheduler, PARTIAL_WAKE_LOCK,
				getWakeLockTag(), 1, MINUTES);
	}

	@Override
	protected int getProcessId() {
		return android.os.Process.myPid();
	}

	@Override
	protected long getLastUpdateTime() {
		try {
			PackageManager pm = appContext.getPackageManager();
			PackageInfo pi = pm.getPackageInfo(appContext.getPackageName(), 0);
			return pi.lastUpdateTime;
		} catch (NameNotFoundException e) {
			throw new AssertionError(e);
		}
	}

	@Override
	protected void enableNetwork(boolean enable) throws IOException {
		if (!state.isRunning()) return;
		if (enable) wakeLock.acquire();
		super.enableNetwork(enable);
		if (!enable) wakeLock.release();
	}

	@Override
	public void stop() {
		super.stop();
		wakeLock.release();
	}

	private String getWakeLockTag() {
		PackageManager pm = appContext.getPackageManager();
		for (PackageInfo info : pm.getInstalledPackages(0)) {
			String name = info.packageName.toLowerCase();
			if (name.startsWith("com.huawei.powergenie")) {
				return "LocationManagerService";
			} else if (name.startsWith("com.evenwell.powermonitor")) {
				return "AudioIn";
			}
		}
		return getClass().getSimpleName();
	}
}
