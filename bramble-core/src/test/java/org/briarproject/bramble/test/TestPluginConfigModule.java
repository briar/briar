package org.briarproject.bramble.test;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.PluginConfig;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginFactory;
import org.briarproject.bramble.api.plugin.simplex.SimplexPlugin;
import org.briarproject.bramble.api.plugin.simplex.SimplexPluginCallback;
import org.briarproject.bramble.api.plugin.simplex.SimplexPluginFactory;

import java.util.Collection;

import javax.annotation.Nullable;

import dagger.Module;
import dagger.Provides;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.briarproject.bramble.test.TestUtils.getTransportId;

@Module
public class TestPluginConfigModule {

	public static final TransportId TRANSPORT_ID = getTransportId();
	public static final int MAX_LATENCY = 2 * 60 * 1000; // 2 minutes

	@NotNullByDefault
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
		@Nullable
		public SimplexPlugin createPlugin(SimplexPluginCallback callback) {
			return null;
		}
	};

	@Provides
	PluginConfig providePluginConfig() {
		@NotNullByDefault
		PluginConfig pluginConfig = new PluginConfig() {

			@Override
			public Collection<DuplexPluginFactory> getDuplexFactories() {
				return emptyList();
			}

			@Override
			public Collection<SimplexPluginFactory> getSimplexFactories() {
				return singletonList(simplex);
			}

			@Override
			public boolean shouldPoll() {
				return false;
			}
		};
		return pluginConfig;
	}
}
