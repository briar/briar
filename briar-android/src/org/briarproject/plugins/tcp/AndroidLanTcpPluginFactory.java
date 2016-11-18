package org.briarproject.plugins.tcp;

import android.content.Context;

import org.briarproject.api.TransportId;
import org.briarproject.api.plugins.Backoff;
import org.briarproject.api.plugins.BackoffFactory;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.plugins.duplex.DuplexPluginFactory;

import java.util.concurrent.Executor;

import static org.briarproject.api.plugins.LanTcpConstants.ID;

public class AndroidLanTcpPluginFactory implements DuplexPluginFactory {

	private static final int MAX_LATENCY = 30 * 1000; // 30 seconds
	private static final int MAX_IDLE_TIME = 30 * 1000; // 30 seconds
	private static final int MIN_POLLING_INTERVAL = 60 * 1000; // 1 minute
	private static final int MAX_POLLING_INTERVAL = 10 * 60 * 1000; // 10 mins
	private static final double BACKOFF_BASE = 1.2;

	private final Executor ioExecutor;
	private final BackoffFactory backoffFactory;
	private final Context appContext;

	public AndroidLanTcpPluginFactory(Executor ioExecutor,
			BackoffFactory backoffFactory, Context appContext) {
		this.ioExecutor = ioExecutor;
		this.backoffFactory = backoffFactory;
		this.appContext = appContext;
	}

	@Override
	public TransportId getId() {
		return ID;
	}

	@Override
	public int getMaxLatency() {
		return MAX_LATENCY;
	}

	@Override
	public DuplexPlugin createPlugin(DuplexPluginCallback callback) {
		Backoff backoff = backoffFactory.createBackoff(MIN_POLLING_INTERVAL,
				MAX_POLLING_INTERVAL, BACKOFF_BASE);
		return new AndroidLanTcpPlugin(ioExecutor, backoff, appContext,
				callback, MAX_LATENCY, MAX_IDLE_TIME);
	}
}
