package net.sf.briar.plugins.droidtooth;

import java.util.concurrent.Executor;

import net.sf.briar.api.android.AndroidExecutor;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexPluginFactory;
import net.sf.briar.api.protocol.TransportId;
import android.content.Context;

public class DroidtoothPluginFactory implements DuplexPluginFactory {

	private static final long POLLING_INTERVAL = 3L * 60L * 1000L; // 3 mins

	private final Executor pluginExecutor;
	private final AndroidExecutor androidExecutor;
	private final Context appContext;

	public DroidtoothPluginFactory(@PluginExecutor Executor pluginExecutor,
			AndroidExecutor androidExecutor, Context appContext) {
		this.pluginExecutor = pluginExecutor;
		this.androidExecutor = androidExecutor;
		this.appContext = appContext;
	}

	public TransportId getId() {
		return DroidtoothPlugin.ID;
	}

	public DuplexPlugin createPlugin(DuplexPluginCallback callback) {
		return new DroidtoothPlugin(pluginExecutor, androidExecutor, appContext,
				callback, POLLING_INTERVAL);
	}
}
