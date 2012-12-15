package net.sf.briar.plugins.bluetooth;

import java.util.concurrent.Executor;

import net.sf.briar.api.clock.Clock;
import net.sf.briar.api.clock.SystemClock;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexPluginFactory;
import net.sf.briar.api.protocol.TransportId;

public class BluetoothPluginFactory implements DuplexPluginFactory {

	private static final long POLLING_INTERVAL = 3L * 60L * 1000L; // 3 mins

	private final Executor pluginExecutor;
	private final Clock clock;

	public BluetoothPluginFactory(@PluginExecutor Executor pluginExecutor) {
		this.pluginExecutor = pluginExecutor;
		clock = new SystemClock();
	}

	public TransportId getId() {
		return BluetoothPlugin.ID;
	}

	public DuplexPlugin createPlugin(DuplexPluginCallback callback) {
		return new BluetoothPlugin(pluginExecutor, clock, callback,
				POLLING_INTERVAL);
	}
}
