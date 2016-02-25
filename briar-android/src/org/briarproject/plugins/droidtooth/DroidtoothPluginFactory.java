package org.briarproject.plugins.droidtooth;

import android.content.Context;

import org.briarproject.api.TransportId;
import org.briarproject.api.android.AndroidExecutor;
import org.briarproject.api.plugins.Backoff;
import org.briarproject.api.plugins.BackoffFactory;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.plugins.duplex.DuplexPluginFactory;

import java.security.SecureRandom;
import java.util.concurrent.Executor;

public class DroidtoothPluginFactory implements DuplexPluginFactory {

	private static final int MAX_LATENCY = 30 * 1000; // 30 seconds
	private static final int MIN_POLLING_INTERVAL = 2 * 60 * 1000; // 2 minutes
	private static final int MAX_POLLING_INTERVAL = 60 * 60 * 1000; // 1 hour
	private static final double BACKOFF_BASE = 1.2;

	private final Executor ioExecutor;
	private final AndroidExecutor androidExecutor;
	private final Context appContext;
	private final SecureRandom secureRandom;
	private final BackoffFactory backoffFactory;

	public DroidtoothPluginFactory(Executor ioExecutor,
			AndroidExecutor androidExecutor, Context appContext,
			SecureRandom secureRandom, BackoffFactory backoffFactory) {
		this.ioExecutor = ioExecutor;
		this.androidExecutor = androidExecutor;
		this.appContext = appContext;
		this.secureRandom = secureRandom;
		this.backoffFactory = backoffFactory;
	}

	public TransportId getId() {
		return DroidtoothPlugin.ID;
	}

	public DuplexPlugin createPlugin(DuplexPluginCallback callback) {
		Backoff backoff = backoffFactory.createBackoff(MIN_POLLING_INTERVAL,
				MAX_POLLING_INTERVAL, BACKOFF_BASE);
		return new DroidtoothPlugin(ioExecutor, androidExecutor, appContext,
				secureRandom, backoff, callback, MAX_LATENCY);
	}
}
