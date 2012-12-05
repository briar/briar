package net.sf.briar.api.plugins.simplex;

import java.util.concurrent.Executor;

import net.sf.briar.api.android.AndroidExecutor;
import net.sf.briar.api.lifecycle.ShutdownManager;
import net.sf.briar.api.protocol.TransportId;
import android.content.Context;

public interface SimplexPluginFactory {

	TransportId getId();

	SimplexPlugin createPlugin(Executor pluginExecutor,
			AndroidExecutor androidExecutor, Context appContext,
			ShutdownManager shutdownManager, SimplexPluginCallback callback);
}
