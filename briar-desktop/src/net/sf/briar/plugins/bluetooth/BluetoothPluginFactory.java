package net.sf.briar.plugins.bluetooth;

import java.security.SecureRandom;
import java.util.concurrent.Executor;

import net.sf.briar.api.TransportId;
import net.sf.briar.api.clock.Clock;
import net.sf.briar.api.clock.SystemClock;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexPluginFactory;

public class BluetoothPluginFactory implements DuplexPluginFactory {

	private static final int MAX_FRAME_LENGTH = 1024;
	private static final long MAX_LATENCY = 60 * 1000; // 1 minute
	private static final long POLLING_INTERVAL = 3 * 60 * 1000; // 3 minutes

	private final Executor pluginExecutor;
	private final SecureRandom secureRandom;
	private final Clock clock;

	public BluetoothPluginFactory(Executor pluginExecutor,
			SecureRandom secureRandom) {
		this.pluginExecutor = pluginExecutor;
		this.secureRandom = secureRandom;
		clock = new SystemClock();
	}

	public TransportId getId() {
		return BluetoothPlugin.ID;
	}

	public DuplexPlugin createPlugin(DuplexPluginCallback callback) {
		return new BluetoothPlugin(pluginExecutor, clock, secureRandom,
				callback, MAX_FRAME_LENGTH, MAX_LATENCY, POLLING_INTERVAL);
	}
}
