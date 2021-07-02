package org.briarproject.bramble.test;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.plugin.PluginConfig;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginFactory;
import org.briarproject.bramble.api.plugin.simplex.SimplexPlugin;
import org.briarproject.bramble.api.plugin.simplex.SimplexPluginFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import dagger.Module;
import dagger.Provides;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.briarproject.bramble.test.TestUtils.getTransportId;

@Module
public class TestPluginConfigModule {

	public static final TransportId SIMPLEX_TRANSPORT_ID = getTransportId();
	public static final TransportId DUPLEX_TRANSPORT_ID = getTransportId();
	private static final int MAX_LATENCY = 30_000; // 30 seconds

	private final TransportId simplexTransportId, duplexTransportId;

	public TestPluginConfigModule() {
		this(SIMPLEX_TRANSPORT_ID, DUPLEX_TRANSPORT_ID);
	}

	public TestPluginConfigModule(TransportId simplexTransportId,
			TransportId duplexTransportId) {
		this.simplexTransportId = simplexTransportId;
		this.duplexTransportId = duplexTransportId;
	}

	@NotNullByDefault
	private final SimplexPluginFactory simplex = new SimplexPluginFactory() {

		@Override
		public TransportId getId() {
			return simplexTransportId;
		}

		@Override
		public int getMaxLatency() {
			return MAX_LATENCY;
		}

		@Override
		@Nullable
		public SimplexPlugin createPlugin(PluginCallback callback) {
			return null;
		}
	};

	@NotNullByDefault
	private final DuplexPluginFactory duplex = new DuplexPluginFactory() {

		@Override
		public TransportId getId() {
			return duplexTransportId;
		}

		@Override
		public int getMaxLatency() {
			return MAX_LATENCY;
		}

		@Nullable
		@Override
		public DuplexPlugin createPlugin(PluginCallback callback) {
			return null;
		}
	};

	@Provides
	public PluginConfig providePluginConfig() {
		@NotNullByDefault
		PluginConfig pluginConfig = new PluginConfig() {

			@Override
			public Collection<DuplexPluginFactory> getDuplexFactories() {
				return singletonList(duplex);
			}

			@Override
			public Collection<SimplexPluginFactory> getSimplexFactories() {
				return singletonList(simplex);
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
