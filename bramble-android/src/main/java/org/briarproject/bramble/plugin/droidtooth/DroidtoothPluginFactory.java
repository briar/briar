package org.briarproject.bramble.plugin.droidtooth;

import android.content.Context;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.BackoffFactory;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginCallback;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginFactory;
import org.briarproject.bramble.api.system.AndroidExecutor;

import java.security.SecureRandom;
import java.util.concurrent.Executor;

import javax.annotation.concurrent.Immutable;

import static org.briarproject.bramble.api.plugin.BluetoothConstants.ID;

@Immutable
@NotNullByDefault
public class DroidtoothPluginFactory implements DuplexPluginFactory {

	private static final int MAX_LATENCY = 30 * 1000; // 30 seconds
	private static final int MIN_POLLING_INTERVAL = 60 * 1000; // 1 minute
	private static final int MAX_POLLING_INTERVAL = 10 * 60 * 1000; // 10 mins
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
		return new DroidtoothPlugin(ioExecutor, androidExecutor, appContext,
				secureRandom, backoff, callback, MAX_LATENCY);
	}
}
