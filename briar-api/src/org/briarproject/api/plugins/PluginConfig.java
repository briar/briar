package org.briarproject.api.plugins;

import org.briarproject.api.plugins.duplex.DuplexPluginFactory;
import org.briarproject.api.plugins.simplex.SimplexPluginFactory;

import java.util.Collection;

public interface PluginConfig {

	Collection<DuplexPluginFactory> getDuplexFactories();

	Collection<SimplexPluginFactory> getSimplexFactories();
}
