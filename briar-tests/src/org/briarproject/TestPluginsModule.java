package org.briarproject;

import org.briarproject.api.TransportId;
import org.briarproject.api.plugins.PluginConfig;
import org.briarproject.api.plugins.duplex.DuplexPluginFactory;
import org.briarproject.api.plugins.simplex.SimplexPlugin;
import org.briarproject.api.plugins.simplex.SimplexPluginCallback;
import org.briarproject.api.plugins.simplex.SimplexPluginFactory;

import java.util.Collection;
import java.util.Collections;

import dagger.Module;
import dagger.Provides;

@Module
public class TestPluginsModule {

	public static final TransportId TRANSPORT_ID = new TransportId("id");
	public static final int MAX_LATENCY = 2 * 60 * 1000; // 2 minutes

	private final SimplexPluginFactory simplex = new SimplexPluginFactory() {

		@Override
		public TransportId getId() {
			return TRANSPORT_ID;
		}

		@Override
		public int getMaxLatency() {
			return MAX_LATENCY;
		}

		@Override
		public SimplexPlugin createPlugin(SimplexPluginCallback callback) {
			return null;
		}
	};

	@Provides
	PluginConfig providePluginConfig() {
		return new PluginConfig() {

			@Override
			public Collection<DuplexPluginFactory> getDuplexFactories() {
				return Collections.emptyList();
			}

			@Override
			public Collection<SimplexPluginFactory> getSimplexFactories() {
				return Collections.singletonList(simplex);
			}
		};
	}
}
