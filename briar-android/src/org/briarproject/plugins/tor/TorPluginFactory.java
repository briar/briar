package org.briarproject.plugins.tor;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import org.briarproject.api.TransportId;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.plugins.duplex.DuplexPluginFactory;
import org.briarproject.api.system.LocationUtils;

import android.content.Context;
import android.os.Build;

public class TorPluginFactory implements DuplexPluginFactory {

	private static final Logger LOG =
			Logger.getLogger(TorPluginFactory.class.getName());

	private static final int MAX_FRAME_LENGTH = 1024;
	private static final int MAX_LATENCY = 30 * 1000; // 30 seconds
	private static final int MAX_IDLE_TIME = 30 * 1000; // 30 seconds
	private static final int POLLING_INTERVAL = 3 * 60 * 1000; // 3 minutes

	private final Executor ioExecutor;
	private final Context appContext;
	private final LocationUtils locationUtils;

	public TorPluginFactory(Executor ioExecutor, Context appContext,
			LocationUtils locationUtils) {
		this.ioExecutor = ioExecutor;
		this.appContext = appContext;
		this.locationUtils = locationUtils;
	}

	public TransportId getId() {
		return TorPlugin.ID;
	}

	public DuplexPlugin createPlugin(DuplexPluginCallback callback) {
		// Check that we have a Tor binary for this architecture
		if(!Build.CPU_ABI.startsWith("armeabi")) {
			LOG.info("Tor is not supported on this architecture");
			return null;
		}
		return new TorPlugin(ioExecutor,appContext, locationUtils, callback,
				MAX_FRAME_LENGTH, MAX_LATENCY, MAX_IDLE_TIME, POLLING_INTERVAL);
	}
}
