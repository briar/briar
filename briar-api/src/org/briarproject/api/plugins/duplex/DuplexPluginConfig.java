package org.briarproject.api.plugins.duplex;

import java.util.Collection;

public interface DuplexPluginConfig {

	Collection<DuplexPluginFactory> getFactories();
}
