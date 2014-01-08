package net.sf.briar.plugins;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Executor;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.lifecycle.ShutdownManager;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.duplex.DuplexPluginConfig;
import net.sf.briar.api.plugins.duplex.DuplexPluginFactory;
import net.sf.briar.api.plugins.simplex.SimplexPluginConfig;
import net.sf.briar.api.plugins.simplex.SimplexPluginFactory;
import net.sf.briar.api.reliability.ReliabilityLayerFactory;
import net.sf.briar.api.system.FileUtils;
import net.sf.briar.plugins.bluetooth.BluetoothPluginFactory;
import net.sf.briar.plugins.file.RemovableDrivePluginFactory;
import net.sf.briar.plugins.modem.ModemPluginFactory;
import net.sf.briar.plugins.tcp.LanTcpPluginFactory;
import net.sf.briar.plugins.tcp.WanTcpPluginFactory;

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
