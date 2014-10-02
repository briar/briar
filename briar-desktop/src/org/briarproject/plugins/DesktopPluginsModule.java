package org.briarproject.plugins;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Executor;

import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.lifecycle.IoExecutor;
import org.briarproject.api.lifecycle.ShutdownManager;
import org.briarproject.api.plugins.duplex.DuplexPluginConfig;
import org.briarproject.api.plugins.duplex.DuplexPluginFactory;
import org.briarproject.api.plugins.simplex.SimplexPluginConfig;
import org.briarproject.api.plugins.simplex.SimplexPluginFactory;
import org.briarproject.api.reliability.ReliabilityLayerFactory;
import org.briarproject.api.system.FileUtils;
import org.briarproject.plugins.bluetooth.BluetoothPluginFactory;
import org.briarproject.plugins.file.RemovableDrivePluginFactory;
import org.briarproject.plugins.modem.ModemPluginFactory;
import org.briarproject.plugins.tcp.LanTcpPluginFactory;
import org.briarproject.plugins.tcp.WanTcpPluginFactory;

import com.google.inject.Provides;

public class DesktopPluginsModule extends PluginsModule {

	@Provides
	SimplexPluginConfig getSimplexPluginConfig(@IoExecutor Executor ioExecutor,
			FileUtils fileUtils) {
		SimplexPluginFactory removable =
				new RemovableDrivePluginFactory(ioExecutor, fileUtils);
		final Collection<SimplexPluginFactory> factories =
				Arrays.asList(removable);
		return new SimplexPluginConfig() {
			public Collection<SimplexPluginFactory> getFactories() {
				return factories;
			}
		};
	}

	@Provides
	DuplexPluginConfig getDuplexPluginConfig(@IoExecutor Executor ioExecutor,
			CryptoComponent crypto, ReliabilityLayerFactory reliabilityFactory,
			ShutdownManager shutdownManager) {
		DuplexPluginFactory bluetooth = new BluetoothPluginFactory(
				ioExecutor, crypto.getSecureRandom());
		DuplexPluginFactory modem = new ModemPluginFactory(ioExecutor,
				reliabilityFactory);
		DuplexPluginFactory lan = new LanTcpPluginFactory(ioExecutor);
		DuplexPluginFactory wan = new WanTcpPluginFactory(ioExecutor,
				shutdownManager);
		final Collection<DuplexPluginFactory> factories =
				Arrays.asList(bluetooth, modem, lan, wan);
		return new DuplexPluginConfig() {
			public Collection<DuplexPluginFactory> getFactories() {
				return factories;
			}
		};
	}
}
