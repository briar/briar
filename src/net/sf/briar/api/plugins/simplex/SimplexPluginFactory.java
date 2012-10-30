package net.sf.briar.api.plugins.simplex;

import java.util.concurrent.Executor;

import net.sf.briar.api.android.AndroidExecutor;

import android.content.Context;

public interface SimplexPluginFactory {

	SimplexPlugin createPlugin(Executor pluginExecutor,
			AndroidExecutor androidExecutor, Context appContext,
			SimplexPluginCallback callback);
}
