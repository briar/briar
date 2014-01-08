package net.sf.briar.plugins.droidtooth;

import java.security.SecureRandom;
import java.util.concurrent.Executor;

import net.sf.briar.api.TransportId;
import net.sf.briar.api.android.AndroidExecutor;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexPluginFactory;
import net.sf.briar.api.system.Clock;
import net.sf.briar.api.system.SystemClock;
import android.content.Context;

public class DroidtoothPluginFactory implements DuplexPluginFactory {

	private static final int MAX_FRAME_LENGTH = 1024;
	private static final long MAX_LATENCY = 60 * 1000; // 1 minute
	private static final long POLLING_INTERVAL = 3 * 60 * 1000; // 3 minutes

	private final Executor pluginExecutor;
	private final AndroidExecutor androidExecutor;
	private final Context appContext;
	private final SecureRandom secureRandom;
	private final Clock clock;

	public DroidtoothPluginFactory(Executor pluginExecutor,
			AndroidExecutor androidExecutor, Context appContext,
			SecureRandom secureRandom) {
		this.pluginExecutor = pluginExecutor;
		this.androidExecutor = androidExecutor;
		this.appContext = appContext;
		this.secureRandom = secureRandom;
		clock = new SystemClock();
	}

	public TransportId getId() {
		return DroidtoothPlugin.ID;
	}

	public DuplexPlugin createPlugin(DuplexPluginCallback callback) {
		return new DroidtoothPlugin(pluginExecutor, androidExecutor, appContext,
				secureRandom, clock, callback, MAX_FRAME_LENGTH, MAX_LATENCY,
				POLLING_INTERVAL);
	}
}
