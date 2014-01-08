package org.briarproject.plugins.tor;

import java.util.concurrent.Executor;

import org.briarproject.api.TransportId;
import org.briarproject.api.lifecycle.ShutdownManager;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.plugins.duplex.DuplexPluginFactory;
import android.content.Context;
import android.os.Build;

public class TorPluginFactory implements DuplexPluginFactory {

	private static final int MAX_FRAME_LENGTH = 1024;
	private static final long MAX_LATENCY = 60 * 1000; // 1 minute
	private static final long POLLING_INTERVAL = 3 * 60 * 1000; // 3 minutes

	private final Executor pluginExecutor;
	private final Context appContext;
	private final ShutdownManager shutdownManager;

	public TorPluginFactory(Executor pluginExecutor, Context appContext,
			ShutdownManager shutdownManager) {
		this.pluginExecutor = pluginExecutor;
		this.appContext = appContext;
		this.shutdownManager = shutdownManager;
	}

	public TransportId getId() {
		return TorPlugin.ID;
	}

	public DuplexPlugin createPlugin(DuplexPluginCallback callback) {
		// Check that we have a Tor binary for this architecture
		if(!Build.CPU_ABI.startsWith("armeabi")) return null;
		return new TorPlugin(pluginExecutor,appContext, shutdownManager,
				callback, MAX_FRAME_LENGTH, MAX_LATENCY, POLLING_INTERVAL);
	}
}
