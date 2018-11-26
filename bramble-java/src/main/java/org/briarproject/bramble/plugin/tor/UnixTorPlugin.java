package org.briarproject.bramble.plugin.tor;

import com.sun.jna.Library;
import com.sun.jna.Native;

import org.briarproject.bramble.api.battery.BatteryManager;
import org.briarproject.bramble.api.network.NetworkManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginCallback;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.LocationUtils;
import org.briarproject.bramble.api.system.ResourceProvider;

import java.io.File;
import java.util.concurrent.Executor;

import javax.net.SocketFactory;

@NotNullByDefault
class UnixTorPlugin extends JavaTorPlugin {

	UnixTorPlugin(Executor ioExecutor, NetworkManager networkManager,
			LocationUtils locationUtils, SocketFactory torSocketFactory,
			Clock clock, ResourceProvider resourceProvider,
			CircumventionProvider circumventionProvider,
			BatteryManager batteryManager, Backoff backoff,
			DuplexPluginCallback callback, String architecture, int maxLatency,
			int maxIdleTime, File torDirectory) {
		super(ioExecutor, networkManager, locationUtils, torSocketFactory,
				clock, resourceProvider, circumventionProvider, batteryManager,
				backoff, callback, architecture, maxLatency, maxIdleTime,
				torDirectory);
	}

	@Override
	protected int getProcessId() {
		return CLibrary.INSTANCE.getpid();
	}

	private interface CLibrary extends Library {

		CLibrary INSTANCE = (CLibrary) Native.loadLibrary("c", CLibrary.class);

		int getpid();
	}
}
