package net.sf.briar.plugins.bluetooth;

import java.security.SecureRandom;
import java.util.concurrent.Executor;

import net.sf.briar.api.clock.Clock;
import net.sf.briar.api.clock.SystemClock;
import net.sf.briar.api.messaging.TransportId;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexPluginFactory;

public class BluetoothPluginFactory implements DuplexPluginFactory {

	private static final long MAX_LATENCY = 60 * 1000; // 1 minute
	private static final long POLLING_INTERVAL = 3 * 60 * 1000; // 3 minutes

	private final Executor pluginExecutor;
	private final SecureRandom secureRandom;
	private final Clock clock;

	public BluetoothPluginFactory(@PluginExecutor Executor pluginExecutor,
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
				callback, MAX_LATENCY, POLLING_INTERVAL);
	}
}
