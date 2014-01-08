package org.briarproject.api.plugins.simplex;

import org.briarproject.api.TransportId;

public interface SimplexPluginFactory {

	TransportId getId();

	SimplexPlugin createPlugin(SimplexPluginCallback callback);
}
