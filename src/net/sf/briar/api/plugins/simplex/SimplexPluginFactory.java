package net.sf.briar.api.plugins.simplex;

import java.util.concurrent.Executor;

import net.sf.briar.api.android.AndroidExecutor;
import net.sf.briar.api.lifecycle.ShutdownManager;

import android.content.Context;

public interface SimplexPluginFactory {

	SimplexPlugin createPlugin(Executor pluginExecutor,
			AndroidExecutor androidExecutor, Context appContext,
			ShutdownManager shutdownManager, SimplexPluginCallback callback);
}
