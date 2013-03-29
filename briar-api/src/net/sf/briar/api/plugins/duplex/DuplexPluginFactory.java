package net.sf.briar.api.plugins.duplex;

import net.sf.briar.api.TransportId;

public interface DuplexPluginFactory {

	TransportId getId();

	DuplexPlugin createPlugin(DuplexPluginCallback callback);
}
