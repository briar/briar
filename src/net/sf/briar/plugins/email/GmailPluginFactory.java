package net.sf.briar.plugins.email;

import java.util.concurrent.Executor;

import net.sf.briar.api.plugins.simplex.SimplexPlugin;
import net.sf.briar.api.plugins.simplex.SimplexPluginCallback;
import net.sf.briar.api.plugins.simplex.SimplexPluginFactory;

public class GmailPluginFactory implements SimplexPluginFactory {

	public SimplexPlugin createPlugin(Executor pluginExecutor,
			SimplexPluginCallback callback) {
		return new GmailPlugin(pluginExecutor, callback);
	}
}
