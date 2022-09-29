package org.briarproject.bramble.api.plugin.duplex;

import org.briarproject.bramble.api.plugin.PluginFactory;
import org.briarproject.nullsafety.NotNullByDefault;

/**
 * Factory for creating a plugin for a duplex transport.
 */
@NotNullByDefault
public interface DuplexPluginFactory extends PluginFactory<DuplexPlugin> {
}
