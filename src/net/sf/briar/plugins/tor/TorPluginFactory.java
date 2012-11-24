package net.sf.briar.plugins.tor;

import java.util.concurrent.Executor;

import net.sf.briar.api.android.AndroidExecutor;
import net.sf.briar.api.lifecycle.ShutdownManager;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexPluginFactory;
import net.sf.briar.api.protocol.TransportId;

import org.h2.util.StringUtils;

import android.content.Context;

public class TorPluginFactory implements DuplexPluginFactory {

	private static final long POLLING_INTERVAL = 15L * 60L * 1000L; // 15 mins

	public TransportId getId() {
		return TorPlugin.ID;
	}

	public DuplexPlugin createPlugin(@PluginExecutor Executor pluginExecutor,
			AndroidExecutor androidExecutor, Context appContext,
			ShutdownManager shutdownManager, DuplexPluginCallback callback) {
		// This plugin is not enabled by default
		String enabled = callback.getConfig().get("enabled");
		if(StringUtils.isNullOrEmpty(enabled)) return null;
		return new TorPlugin(pluginExecutor, callback, POLLING_INTERVAL);
	}
}
