package net.sf.briar.api.plugins.duplex;

import java.util.concurrent.Executor;

import net.sf.briar.api.android.AndroidExecutor;
import net.sf.briar.api.lifecycle.ShutdownManager;

import android.content.Context;

public interface DuplexPluginFactory {

	DuplexPlugin createPlugin(Executor pluginExecutor,
			AndroidExecutor androidExecutor, Context appContext,
			ShutdownManager shutdownManager, DuplexPluginCallback callback);
}
