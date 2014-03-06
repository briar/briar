package org.briarproject.plugins;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executor;

import org.briarproject.api.android.AndroidExecutor;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.lifecycle.ShutdownManager;
import org.briarproject.api.plugins.PluginExecutor;
import org.briarproject.api.plugins.duplex.DuplexPluginConfig;
import org.briarproject.api.plugins.duplex.DuplexPluginFactory;
import org.briarproject.api.plugins.simplex.SimplexPluginConfig;
import org.briarproject.api.plugins.simplex.SimplexPluginFactory;
import org.briarproject.api.system.LocationUtils;
import org.briarproject.plugins.droidtooth.DroidtoothPluginFactory;
import org.briarproject.plugins.tcp.LanTcpPluginFactory;
import org.briarproject.plugins.tor.TorPluginFactory;

import android.app.Application;
import android.content.Context;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class AndroidPluginsModule extends AbstractModule {

	protected void configure() {}

	@Provides
	SimplexPluginConfig getSimplexPluginConfig() {
		return new SimplexPluginConfig() {
			public Collection<SimplexPluginFactory> getFactories() {
				return Collections.emptyList();
			}
		};
	}

	@Provides
	DuplexPluginConfig getDuplexPluginConfig(
			@PluginExecutor Executor pluginExecutor,
			AndroidExecutor androidExecutor, Application app,
			CryptoComponent crypto, LocationUtils locationUtils,
			ShutdownManager shutdownManager) {
		Context ctx = app.getApplicationContext();
		DuplexPluginFactory droidtooth = new DroidtoothPluginFactory(
				pluginExecutor, androidExecutor, ctx, crypto.getSecureRandom());
		DuplexPluginFactory tor = new TorPluginFactory(pluginExecutor,
				ctx, locationUtils, shutdownManager);
		DuplexPluginFactory lan = new LanTcpPluginFactory(pluginExecutor);
		final Collection<DuplexPluginFactory> factories =
				Arrays.asList(droidtooth, tor, lan);
		return new DuplexPluginConfig() {
			public Collection<DuplexPluginFactory> getFactories() {
				return factories;
			}
		};
	}
}
