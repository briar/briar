package org.briarproject.bramble.plugin;

import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.lifecycle.ShutdownManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.BackoffFactory;
import org.briarproject.bramble.api.plugin.PluginConfig;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginFactory;
import org.briarproject.bramble.api.plugin.simplex.SimplexPluginFactory;
import org.briarproject.bramble.api.reliability.ReliabilityLayerFactory;
import org.briarproject.bramble.plugin.bluetooth.JavaBluetoothPluginFactory;
import org.briarproject.bramble.plugin.modem.ModemPluginFactory;
import org.briarproject.bramble.plugin.tcp.LanTcpPluginFactory;
import org.briarproject.bramble.plugin.tcp.WanTcpPluginFactory;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.concurrent.Executor;

import dagger.Module;
import dagger.Provides;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

@Module
public class DesktopPluginModule extends PluginModule {

	@Provides
	PluginConfig getPluginConfig(@IoExecutor Executor ioExecutor,
			SecureRandom random, BackoffFactory backoffFactory,
			ReliabilityLayerFactory reliabilityFactory,
			ShutdownManager shutdownManager, EventBus eventBus) {
		DuplexPluginFactory bluetooth =
				new JavaBluetoothPluginFactory(ioExecutor, random, eventBus,
						backoffFactory);
		DuplexPluginFactory modem = new ModemPluginFactory(ioExecutor,
				reliabilityFactory);
		DuplexPluginFactory lan = new LanTcpPluginFactory(ioExecutor, eventBus,
				backoffFactory);
		DuplexPluginFactory wan = new WanTcpPluginFactory(ioExecutor, eventBus,
				backoffFactory, shutdownManager);
		Collection<DuplexPluginFactory> duplex =
				asList(bluetooth, modem, lan, wan);
		@NotNullByDefault
		PluginConfig pluginConfig = new PluginConfig() {

			@Override
			public Collection<DuplexPluginFactory> getDuplexFactories() {
				return duplex;
			}

			@Override
			public Collection<SimplexPluginFactory> getSimplexFactories() {
				return emptyList();
			}

			@Override
			public boolean shouldPoll() {
				return true;
			}
		};
		return pluginConfig;
	}
}
