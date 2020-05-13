package org.briarproject.bramble.api.plugin;

import org.briarproject.bramble.api.Pair;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginFactory;
import org.briarproject.bramble.api.plugin.simplex.SimplexPluginFactory;

import java.util.Collection;
import java.util.List;

@NotNullByDefault
public interface PluginConfig {

	Collection<DuplexPluginFactory> getDuplexFactories();

	Collection<SimplexPluginFactory> getSimplexFactories();

	boolean shouldPoll();

	/**
	 * Returns a list of transport preferences. For each pair in the list,
	 * the first transport is preferred to the second.
	 */
	List<Pair<TransportId, TransportId>> getTransportPreferences();
}
