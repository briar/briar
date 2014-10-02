package org.briarproject.plugins;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executor;

import org.briarproject.api.android.AndroidExecutor;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.lifecycle.IoExecutor;
import org.briarproject.api.plugins.duplex.DuplexPluginConfig;
import org.briarproject.api.plugins.duplex.DuplexPluginFactory;
import org.briarproject.api.plugins.simplex.SimplexPluginConfig;
import org.briarproject.api.plugins.simplex.SimplexPluginFactory;
import org.briarproject.api.system.LocationUtils;
import org.briarproject.plugins.droidtooth.DroidtoothPluginFactory;
import org.briarproject.plugins.tcp.AndroidLanTcpPluginFactory;
import org.briarproject.plugins.tor.TorPluginFactory;

import android.app.Application;
import android.content.Context;

import com.google.inject.Provides;

public class AndroidPluginsModule extends PluginsModule {

	@Provides
	SimplexPluginConfig getSimplexPluginConfig() {
		return new SimplexPluginConfig() {
			public Collection<SimplexPluginFactory> getFactories() {
				return Collections.emptyList();
			}
		};
	}

	@Provides
	DuplexPluginConfig getDuplexPluginConfig(@IoExecutor Executor ioExecutor,
			AndroidExecutor androidExecutor, Application app,
			CryptoComponent crypto, LocationUtils locationUtils) {
		Context appContext = app.getApplicationContext();
		DuplexPluginFactory bluetooth = new DroidtoothPluginFactory(ioExecutor,
				androidExecutor, appContext, crypto.getSecureRandom());
		DuplexPluginFactory tor = new TorPluginFactory(ioExecutor, appContext,
				locationUtils);
		DuplexPluginFactory lan = new AndroidLanTcpPluginFactory(ioExecutor,
				appContext);
		final Collection<DuplexPluginFactory> factories =
				Arrays.asList(bluetooth, tor, lan);
		return new DuplexPluginConfig() {
			public Collection<DuplexPluginFactory> getFactories() {
				return factories;
			}
		};
	}
}
