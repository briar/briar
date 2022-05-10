package org.briarproject.bramble.plugin.tor.wrapper;

import com.sun.jna.Library;
import com.sun.jna.Native;

import org.briarproject.nullsafety.NotNullByDefault;

import java.io.File;
import java.util.concurrent.Executor;

@NotNullByDefault
public class UnixTorWrapper extends JavaTorWrapper {

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
