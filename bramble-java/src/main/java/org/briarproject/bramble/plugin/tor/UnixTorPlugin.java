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
import java.util.logging.Logger;

import javax.net.SocketFactory;

import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;

@NotNullByDefault
class UnixTorPlugin extends JavaTorPlugin {

	private static final Logger LOG = getLogger(UnixTorPlugin.class.getName());

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
			int maxLatency,
			int maxIdleTime,
			File torDirectory) {
		super(ioExecutor, wakefulIoExecutor, networkManager, locationUtils,
				torSocketFactory, clock, resourceProvider,
				circumventionProvider, batteryManager, backoff,
				torRendezvousCrypto, callback, architecture,
				maxLatency, maxIdleTime, torDirectory);
		boolean isGlibc = isGlibc();
		if (LOG.isLoggable(INFO)) LOG.info("System uses glibc: " + isGlibc);
	}

	@Override
	protected int getProcessId() {
		return CLibrary.INSTANCE.getpid();
	}

	protected boolean isGlibc() {
		try {
			GnuCLibrary glibc = Native.loadLibrary("c", GnuCLibrary.class);
			if (LOG.isLoggable(INFO)) {
				LOG.info("glibc version " + glibc.gnu_get_libc_version());
			}
			return true;
		} catch (UnsatisfiedLinkError e) {
			return false;
		}
	}

	private interface CLibrary extends Library {

		CLibrary INSTANCE = Native.loadLibrary("c", CLibrary.class);

		int getpid();
	}

	private interface GnuCLibrary extends Library {

		String gnu_get_libc_version();
	}
}
