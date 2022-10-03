package org.briarproject.bramble.test;

import org.briarproject.bramble.api.plugin.PluginConfig;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginFactory;
import org.briarproject.bramble.api.plugin.simplex.SimplexPluginFactory;
import org.briarproject.nullsafety.NotNullByDefault;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import dagger.Module;
import dagger.Provides;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;

@Module
public class FakeTorPluginConfigModule {

	@Provides
	PluginConfig providePluginConfig(FakeTorPluginFactory tor) {
		@NotNullByDefault
		PluginConfig pluginConfig = new PluginConfig() {

			@Override
			public Collection<DuplexPluginFactory> getDuplexFactories() {
				return singletonList(tor);
			}

			@Override
			public Collection<SimplexPluginFactory> getSimplexFactories() {
				return emptyList();
			}

			@Override
			public boolean shouldPoll() {
				return false;
			}

			@Override
			public Map<TransportId, List<TransportId>> getTransportPreferences() {
				return emptyMap();
			}

		};
		return pluginConfig;
	}
}
