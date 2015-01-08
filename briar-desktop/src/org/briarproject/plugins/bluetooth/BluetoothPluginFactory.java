package org.briarproject.plugins.bluetooth;

import java.security.SecureRandom;
import java.util.concurrent.Executor;

import org.briarproject.api.TransportId;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.plugins.duplex.DuplexPluginFactory;
import org.briarproject.api.system.Clock;
import org.briarproject.system.SystemClock;

public class BluetoothPluginFactory implements DuplexPluginFactory {

	private static final int MAX_LATENCY = 30 * 1000; // 30 seconds
	private static final int POLLING_INTERVAL = 3 * 60 * 1000; // 3 minutes

	private final Executor ioExecutor;
	private final SecureRandom secureRandom;
	private final Clock clock;

	public BluetoothPluginFactory(Executor ioExecutor,
			SecureRandom secureRandom) {
		this.ioExecutor = ioExecutor;
		this.secureRandom = secureRandom;
		clock = new SystemClock();
	}

	public TransportId getId() {
		return BluetoothPlugin.ID;
	}

	public DuplexPlugin createPlugin(DuplexPluginCallback callback) {
		return new BluetoothPlugin(ioExecutor, clock, secureRandom, callback,
				MAX_LATENCY, POLLING_INTERVAL);
	}
}
