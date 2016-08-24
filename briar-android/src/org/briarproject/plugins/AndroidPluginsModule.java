package org.briarproject.plugins;

import android.app.Application;
import android.content.Context;

import org.briarproject.android.api.AndroidExecutor;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.lifecycle.IoExecutor;
import org.briarproject.api.plugins.BackoffFactory;
import org.briarproject.api.plugins.PluginConfig;
import org.briarproject.api.plugins.duplex.DuplexPluginFactory;
import org.briarproject.api.plugins.simplex.SimplexPluginFactory;
import org.briarproject.api.reporting.DevReporter;
import org.briarproject.api.system.LocationUtils;
import org.briarproject.plugins.droidtooth.DroidtoothPluginFactory;
import org.briarproject.plugins.tcp.AndroidLanTcpPluginFactory;
import org.briarproject.plugins.tor.TorPluginFactory;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executor;

import dagger.Module;
import dagger.Provides;

@Module
public class AndroidPluginsModule {

	@Provides
	public PluginConfig providePluginConfig(@IoExecutor Executor ioExecutor,
			AndroidExecutor androidExecutor,
			SecureRandom random, BackoffFactory backoffFactory, Application app,
			LocationUtils locationUtils, DevReporter reporter,
			EventBus eventBus) {
		Context appContext = app.getApplicationContext();
		DuplexPluginFactory bluetooth = new DroidtoothPluginFactory(ioExecutor,
				androidExecutor, appContext, random, backoffFactory);
		DuplexPluginFactory tor = new TorPluginFactory(ioExecutor, appContext,
				locationUtils, reporter, eventBus);
		DuplexPluginFactory lan = new AndroidLanTcpPluginFactory(ioExecutor,
				backoffFactory, appContext);
		final Collection<DuplexPluginFactory> duplex =
				Arrays.asList(bluetooth, tor, lan);
		return new PluginConfig() {

			@Override
			public Collection<DuplexPluginFactory> getDuplexFactories() {
				return duplex;
			}

			@Override
			public Collection<SimplexPluginFactory> getSimplexFactories() {
				return Collections.emptyList();
			}
		};
	}
}
