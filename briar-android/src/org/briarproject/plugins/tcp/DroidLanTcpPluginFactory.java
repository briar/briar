package org.briarproject.plugins.tcp;

import java.util.concurrent.Executor;

import org.briarproject.api.TransportId;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.plugins.duplex.DuplexPluginFactory;
import org.briarproject.api.system.Clock;
import org.briarproject.api.system.SystemClock;
import android.content.Context;

public class DroidLanTcpPluginFactory implements DuplexPluginFactory {

	private static final int MAX_FRAME_LENGTH = 1024;
	private static final long MAX_LATENCY = 60 * 1000; // 1 minute
	private static final long POLLING_INTERVAL = 60 * 1000; // 1 minute

	private final Executor pluginExecutor;
	private final Context appContext;
	private final Clock clock;

	public DroidLanTcpPluginFactory(Executor pluginExecutor,
			Context appContext) {
		this.pluginExecutor = pluginExecutor;
		this.appContext = appContext;
		clock = new SystemClock();
	}

	public TransportId getId() {
		return LanTcpPlugin.ID;
	}

	public DuplexPlugin createPlugin(DuplexPluginCallback callback) {
		return new DroidLanTcpPlugin(pluginExecutor, appContext, clock,
				callback, MAX_FRAME_LENGTH, MAX_LATENCY, POLLING_INTERVAL);
	}
}
