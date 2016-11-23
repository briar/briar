package org.briarproject.bramble.plugin;

import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.lifecycle.ShutdownManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.BackoffFactory;
import org.briarproject.bramble.api.plugin.PluginConfig;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginFactory;
import org.briarproject.bramble.api.plugin.simplex.SimplexPluginFactory;
import org.briarproject.bramble.api.reliability.ReliabilityLayerFactory;
import org.briarproject.bramble.plugin.bluetooth.BluetoothPluginFactory;
import org.briarproject.bramble.plugin.file.RemovableDrivePluginFactory;
import org.briarproject.bramble.plugin.modem.ModemPluginFactory;
import org.briarproject.bramble.plugin.tcp.LanTcpPluginFactory;
import org.briarproject.bramble.plugin.tcp.WanTcpPluginFactory;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executor;

import dagger.Module;
import dagger.Provides;

@Module
public class DesktopPluginModule extends PluginModule {

	@Provides
	PluginConfig getPluginConfig(@IoExecutor Executor ioExecutor,
			SecureRandom random, BackoffFactory backoffFactory,
			ReliabilityLayerFactory reliabilityFactory,
			ShutdownManager shutdownManager) {
		DuplexPluginFactory bluetooth = new BluetoothPluginFactory(ioExecutor,
				random, backoffFactory);
		DuplexPluginFactory modem = new ModemPluginFactory(ioExecutor,
				reliabilityFactory);
		DuplexPluginFactory lan = new LanTcpPluginFactory(ioExecutor,
				backoffFactory);
		DuplexPluginFactory wan = new WanTcpPluginFactory(ioExecutor,
				backoffFactory, shutdownManager);
		SimplexPluginFactory removable =
				new RemovableDrivePluginFactory(ioExecutor);
		final Collection<SimplexPluginFactory> simplex =
				Collections.singletonList(removable);
		final Collection<DuplexPluginFactory> duplex =
				Arrays.asList(bluetooth, modem, lan, wan);
		@NotNullByDefault
		PluginConfig pluginConfig = new PluginConfig() {

			@Override
			public Collection<DuplexPluginFactory> getDuplexFactories() {
				return duplex;
			}

			@Override
			public Collection<SimplexPluginFactory> getSimplexFactories() {
				return simplex;
			}
		};
		return pluginConfig;
	}
}
