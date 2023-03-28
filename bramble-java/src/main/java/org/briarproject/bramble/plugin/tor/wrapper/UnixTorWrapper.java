package org.briarproject.bramble.plugin.tor.wrapper;

import com.sun.jna.Library;
import com.sun.jna.Native;

import org.briarproject.nullsafety.NotNullByDefault;

import java.io.File;
import java.util.concurrent.Executor;

/**
 * A Tor wrapper for Unix-like operating systems.
 */
@NotNullByDefault
public class UnixTorWrapper extends JavaTorWrapper {

	/**
	 * @param ioExecutor The wrapper will use this executor to run IO tasks,
	 * some of which may run for the lifetime of the wrapper, so the executor
	 * should have an unlimited thread pool.
	 * @param eventExecutor The wrapper will use this executor to call the
	 * {@link Observer observer} (if any). To ensure that events are observed
	 * in the order they occur, this executor should have a single thread (eg
	 * the app's main thread).
	 * @param architecture The processor architecture of the Tor and pluggable
	 * transport binaries.
	 * @param torDirectory The directory where the Tor process should keep its
	 * state.
	 * @param torSocksPort The port number to use for Tor's SOCKS port.
	 * @param torControlPort The port number to use for Tor's control port.
	 */
	public UnixTorWrapper(Executor ioExecutor,
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
		return CLibrary.INSTANCE.getpid();
	}

	private interface CLibrary extends Library {

		CLibrary INSTANCE = Native.loadLibrary("c", CLibrary.class);

		int getpid();
	}
}
