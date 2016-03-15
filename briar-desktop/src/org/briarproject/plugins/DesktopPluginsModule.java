package org.briarproject.plugins;

import org.briarproject.api.lifecycle.IoExecutor;
import org.briarproject.api.lifecycle.ShutdownManager;
import org.briarproject.api.plugins.BackoffFactory;
import org.briarproject.api.plugins.duplex.DuplexPluginConfig;
import org.briarproject.api.plugins.duplex.DuplexPluginFactory;
import org.briarproject.api.plugins.simplex.SimplexPluginConfig;
import org.briarproject.api.plugins.simplex.SimplexPluginFactory;
import org.briarproject.api.reliability.ReliabilityLayerFactory;
import org.briarproject.plugins.bluetooth.BluetoothPluginFactory;
import org.briarproject.plugins.file.RemovableDrivePluginFactory;
import org.briarproject.plugins.modem.ModemPluginFactory;
import org.briarproject.plugins.tcp.LanTcpPluginFactory;
import org.briarproject.plugins.tcp.WanTcpPluginFactory;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executor;

import dagger.Module;
import dagger.Provides;

@Module
public class DesktopPluginsModule extends PluginsModule {

	@Provides
	SimplexPluginConfig getSimplexPluginConfig(
			@IoExecutor Executor ioExecutor) {
		SimplexPluginFactory removable =
				new RemovableDrivePluginFactory(ioExecutor);
		final Collection<SimplexPluginFactory> factories =
				Collections.singletonList(removable);
		return new SimplexPluginConfig() {
			public Collection<SimplexPluginFactory> getFactories() {
				return factories;
			}
		};
	}

	@Provides
	DuplexPluginConfig getDuplexPluginConfig(@IoExecutor Executor ioExecutor,
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
		final Collection<DuplexPluginFactory> factories =
				Arrays.asList(bluetooth, modem, lan, wan);
		return new DuplexPluginConfig() {
			public Collection<DuplexPluginFactory> getFactories() {
				return factories;
			}
		};
	}
}
