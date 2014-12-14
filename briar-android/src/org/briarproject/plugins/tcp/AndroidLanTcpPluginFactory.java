package org.briarproject.plugins.tcp;

import java.util.concurrent.Executor;

import org.briarproject.api.TransportId;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.plugins.duplex.DuplexPluginFactory;

import android.content.Context;

public class AndroidLanTcpPluginFactory implements DuplexPluginFactory {

	private static final int MAX_FRAME_LENGTH = 1024;
	private static final int MAX_LATENCY = 30 * 1000; // 30 seconds
	private static final int MAX_IDLE_TIME = 30 * 1000; // 30 seconds
	private static final int POLLING_INTERVAL = 3 * 60 * 1000; // 3 minutes

	private final Executor ioExecutor;
	private final Context appContext;

	public AndroidLanTcpPluginFactory(Executor ioExecutor, Context appContext) {
		this.ioExecutor = ioExecutor;
		this.appContext = appContext;
	}

	public TransportId getId() {
		return LanTcpPlugin.ID;
	}

	public DuplexPlugin createPlugin(DuplexPluginCallback callback) {
		return new AndroidLanTcpPlugin(ioExecutor, appContext, callback,
				MAX_FRAME_LENGTH, MAX_LATENCY, MAX_IDLE_TIME, POLLING_INTERVAL);
	}
}
