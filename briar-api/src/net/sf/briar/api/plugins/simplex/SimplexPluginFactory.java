package net.sf.briar.api.plugins.simplex;

import net.sf.briar.api.messaging.TransportId;

public interface SimplexPluginFactory {

	TransportId getId();

	SimplexPlugin createPlugin(SimplexPluginCallback callback);
}
