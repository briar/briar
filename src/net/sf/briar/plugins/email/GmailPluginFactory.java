package net.sf.briar.plugins.email;

import java.util.concurrent.Executor;

import net.sf.briar.api.android.AndroidExecutor;
import net.sf.briar.api.plugins.simplex.SimplexPlugin;
import net.sf.briar.api.plugins.simplex.SimplexPluginCallback;
import net.sf.briar.api.plugins.simplex.SimplexPluginFactory;
import android.content.Context;

public class GmailPluginFactory implements SimplexPluginFactory {

	public SimplexPlugin createPlugin(Executor pluginExecutor,
			AndroidExecutor androidExecutor, Context context,
			SimplexPluginCallback callback) {
		return new GmailPlugin(pluginExecutor, callback);
	}
}
