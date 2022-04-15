package org.briarproject.bramble.plugin.tor;

import com.sun.jna.platform.win32.Kernel32;

import org.briarproject.bramble.api.battery.BatteryManager;
import org.briarproject.bramble.api.network.NetworkManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.plugin.PluginException;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.LocationUtils;
import org.briarproject.bramble.api.system.ResourceProvider;

import java.io.File;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;

import javax.net.SocketFactory;

import static java.util.logging.Level.INFO;

@NotNullByDefault
class WindowsTorPlugin extends JavaTorPlugin {

	WindowsTorPlugin(Executor ioExecutor,
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
		return Kernel32.INSTANCE.GetCurrentProcessId();
	}

	@Override
	protected void waitForTorToStart(Process torProcess)
			throws InterruptedException, PluginException {
		// On Windows the RunAsDaemon option has no effect, so Tor won't detach.
		// Wait for the control port to be opened, then continue to read its
		// stdout and stderr in a background thread until it exits.
		BlockingQueue<Boolean> success = new ArrayBlockingQueue<>(1);
		ioExecutor.execute(() -> {
			boolean started = false;
			// Read the process's stdout (and redirected stderr)
			Scanner stdout = new Scanner(torProcess.getInputStream());
			// Log the first line of stdout (contains Tor and library versions)
			if (stdout.hasNextLine()) LOG.info(stdout.nextLine());
			// Startup has succeeded when the control port is open
			while (stdout.hasNextLine()) {
				String line = stdout.nextLine();
				if (!started && line.contains("Opened Control listener")) {
					success.add(true);
					started = true;
				}
			}
			stdout.close();
			// If the control port wasn't opened, startup has failed
			if (!started) success.add(false);
			// Wait for the process to exit
			try {
				int exit = torProcess.waitFor();
				if (LOG.isLoggable(INFO))
					LOG.info("Tor exited with value " + exit);
			} catch (InterruptedException e1) {
				LOG.warning("Interrupted while waiting for Tor to exit");
				Thread.currentThread().interrupt();
			}
		});
		// Wait for the startup result
		if (!success.take()) throw new PluginException();
	}

}
