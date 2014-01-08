package org.briarproject.plugins;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Executor;

import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.lifecycle.ShutdownManager;
import org.briarproject.api.plugins.PluginExecutor;
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

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class DesktopPluginsModule extends AbstractModule {

	public void configure() {}

	@Provides
	SimplexPluginConfig getSimplexPluginConfig(
			@PluginExecutor Executor pluginExecutor, FileUtils fileUtils) {
		SimplexPluginFactory removable =
				new RemovableDrivePluginFactory(pluginExecutor, fileUtils);
		final Collection<SimplexPluginFactory> factories =
				Arrays.asList(removable);
		return new SimplexPluginConfig() {
			public Collection<SimplexPluginFactory> getFactories() {
				return factories;
			}
		};
	}

	@Provides
	DuplexPluginConfig getDuplexPluginConfig(
			@PluginExecutor Executor pluginExecutor,
			CryptoComponent crypto, ReliabilityLayerFactory reliabilityFactory,
			ShutdownManager shutdownManager) {
		DuplexPluginFactory bluetooth = new BluetoothPluginFactory(
				pluginExecutor, crypto.getSecureRandom());
		DuplexPluginFactory modem = new ModemPluginFactory(pluginExecutor,
				reliabilityFactory);
		DuplexPluginFactory lan = new LanTcpPluginFactory(pluginExecutor);
		DuplexPluginFactory wan = new WanTcpPluginFactory(pluginExecutor,
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
