package net.sf.briar.plugins.droidtooth;

import java.security.SecureRandom;
import java.util.concurrent.Executor;

import net.sf.briar.api.TransportId;
import net.sf.briar.api.android.AndroidExecutor;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexPluginFactory;
import android.content.Context;

public class DroidtoothPluginFactory implements DuplexPluginFactory {

	private static final long MAX_LATENCY = 60 * 1000; // 1 minute
	private static final long POLLING_INTERVAL = 3 * 60 * 1000; // 3 minutes

	private final Executor pluginExecutor;
	private final AndroidExecutor androidExecutor;
	private final Context appContext;
	private final SecureRandom secureRandom;

	public DroidtoothPluginFactory(Executor pluginExecutor,
			AndroidExecutor androidExecutor, Context appContext,
			SecureRandom secureRandom) {
		this.pluginExecutor = pluginExecutor;
		this.androidExecutor = androidExecutor;
		this.appContext = appContext;
		this.secureRandom = secureRandom;
	}

	public TransportId getId() {
		return DroidtoothPlugin.ID;
	}

	public DuplexPlugin createPlugin(DuplexPluginCallback callback) {
		return new DroidtoothPlugin(pluginExecutor, androidExecutor, appContext,
				secureRandom, callback, MAX_LATENCY, POLLING_INTERVAL);
	}
}
