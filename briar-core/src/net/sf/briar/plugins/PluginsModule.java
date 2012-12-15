package net.sf.briar.plugins;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.sf.briar.api.android.AndroidExecutor;
import net.sf.briar.api.lifecycle.ShutdownManager;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.PluginManager;
import net.sf.briar.api.plugins.duplex.DuplexPluginConfig;
import net.sf.briar.api.plugins.duplex.DuplexPluginFactory;
import net.sf.briar.api.plugins.simplex.SimplexPluginConfig;
import net.sf.briar.api.plugins.simplex.SimplexPluginFactory;
import net.sf.briar.api.reliability.ReliabilityLayerFactory;
import net.sf.briar.plugins.bluetooth.BluetoothPluginFactory;
import net.sf.briar.plugins.droidtooth.DroidtoothPluginFactory;
import net.sf.briar.plugins.file.RemovableDrivePluginFactory;
import net.sf.briar.plugins.modem.ModemPluginFactory;
import net.sf.briar.plugins.tcp.LanTcpPluginFactory;
import net.sf.briar.plugins.tcp.WanTcpPluginFactory;
import net.sf.briar.plugins.tor.TorPluginFactory;
import net.sf.briar.util.OsUtils;
import android.content.Context;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

public class PluginsModule extends AbstractModule {

	@Override
	protected void configure() {
		// The executor is unbounded, so tasks can be dependent or long-lived
		bind(ExecutorService.class).annotatedWith(
				PluginExecutor.class).toInstance(
						Executors.newCachedThreadPool());
		bind(PluginManager.class).to(
				PluginManagerImpl.class).in(Singleton.class);
		bind(Poller.class).to(PollerImpl.class);
	}

	@Provides
	SimplexPluginConfig getSimplexPluginConfig(
			@PluginExecutor Executor pluginExecutor) {
		final Collection<SimplexPluginFactory> factories =
				new ArrayList<SimplexPluginFactory>();
		if(!OsUtils.isAndroid()) {
			// No simplex plugins for Android
		} else {
			factories.add(new RemovableDrivePluginFactory(pluginExecutor));
		}
		return new SimplexPluginConfig() {
			public Collection<SimplexPluginFactory> getFactories() {
				return factories;
			}
		};
	}

	@Provides
	DuplexPluginConfig getDuplexPluginConfig(
			@PluginExecutor Executor pluginExecutor,
			AndroidExecutor androidExecutor, Context appContext,
			ReliabilityLayerFactory reliabilityFactory,
			ShutdownManager shutdownManager) {
		final Collection<DuplexPluginFactory> factories =
				new ArrayList<DuplexPluginFactory>();
		if(OsUtils.isAndroid()) {
			factories.add(new DroidtoothPluginFactory(pluginExecutor,
					androidExecutor, appContext));
		} else {
			factories.add(new BluetoothPluginFactory(pluginExecutor));
			factories.add(new ModemPluginFactory(pluginExecutor,
					reliabilityFactory));
		}
		factories.add(new LanTcpPluginFactory(pluginExecutor));
		factories.add(new WanTcpPluginFactory(pluginExecutor, shutdownManager));
		factories.add(new TorPluginFactory(pluginExecutor));
		return new DuplexPluginConfig() {
			public Collection<DuplexPluginFactory> getFactories() {
				return factories;
			}
		};
	}
}
