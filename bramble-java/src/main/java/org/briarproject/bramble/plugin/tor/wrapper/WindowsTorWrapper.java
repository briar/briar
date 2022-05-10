package org.briarproject.bramble.plugin.tor.wrapper;

import com.sun.jna.platform.win32.Kernel32;

import org.briarproject.nullsafety.NotNullByDefault;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;

import static java.util.logging.Level.INFO;

@NotNullByDefault
public class WindowsTorWrapper extends JavaTorWrapper {

	public WindowsTorWrapper(Executor ioExecutor,
			Executor eventExecutor,
			String architecture,
			File torDirectory,
			int torSocksPort,
			int torControlPort) {
		super(ioExecutor, eventExecutor, architecture, torDirectory,
				torSocksPort, torControlPort);
	}

	@Override
	protected int getProcessId() {
		return Kernel32.INSTANCE.GetCurrentProcessId();
	}

	@Override
	protected void waitForTorToStart(Process torProcess)
			throws InterruptedException, IOException {
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
		if (!success.take()) throw new IOException();
	}

	@Override
	protected String getExecutableExtension() {
		return ".exe";
	}
}
