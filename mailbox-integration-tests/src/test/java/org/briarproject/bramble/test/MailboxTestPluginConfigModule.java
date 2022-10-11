package org.briarproject.bramble.test;

import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.plugin.PluginConfig;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginFactory;
import org.briarproject.bramble.api.plugin.simplex.SimplexPlugin;
import org.briarproject.bramble.api.plugin.simplex.SimplexPluginFactory;
import org.briarproject.bramble.plugin.file.MailboxPluginFactory;
import org.briarproject.nullsafety.NotNullByDefault;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import dagger.Module;
import dagger.Provides;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.briarproject.bramble.test.TestPluginConfigModule.SIMPLEX_TRANSPORT_ID;

@Module
public class MailboxTestPluginConfigModule {

	private static final int MAX_LATENCY = 30_000; // 30 seconds

	@NotNullByDefault
	private final SimplexPluginFactory simplex = new SimplexPluginFactory() {

		@Override
		public TransportId getId() {
			return SIMPLEX_TRANSPORT_ID;
		}

		@Override
		public long getMaxLatency() {
			return MAX_LATENCY;
		}

		@Override
		@Nullable
		public SimplexPlugin createPlugin(PluginCallback callback) {
			return null;
		}
	};

	@Provides
	PluginConfig providePluginConfig(FakeTorPluginFactory tor,
			MailboxPluginFactory mailboxPluginFactory) {
		@NotNullByDefault
		PluginConfig pluginConfig = new PluginConfig() {

			@Override
			public Collection<DuplexPluginFactory> getDuplexFactories() {
				return singletonList(tor);
			}

			@Override
			public Collection<SimplexPluginFactory> getSimplexFactories() {
				return asList(simplex, mailboxPluginFactory);
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
