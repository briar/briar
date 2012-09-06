package net.sf.briar.plugins.email;

import java.util.concurrent.Executor;

import net.sf.briar.api.plugins.simplex.SimplexPlugin;
import net.sf.briar.api.plugins.simplex.SimplexPluginCallback;
import net.sf.briar.api.plugins.simplex.SimplexPluginFactory;
import net.sf.briar.clock.Clock;

public class GmailPluginFactory implements SimplexPluginFactory {

	public SimplexPlugin createPlugin(Executor pluginExecutor, Clock clock,
			SimplexPluginCallback callback) {
		
		return new GmailPlugin(pluginExecutor, callback);
	}


}
