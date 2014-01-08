package org.briarproject.api.plugins.duplex;

import org.briarproject.api.TransportId;

public interface DuplexPluginFactory {

	TransportId getId();

	DuplexPlugin createPlugin(DuplexPluginCallback callback);
}
