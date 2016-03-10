package org.briarproject.plugins;

import android.app.Application;
import android.content.Context;

import org.briarproject.api.android.PlatformExecutor;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.lifecycle.IoExecutor;
import org.briarproject.api.plugins.BackoffFactory;
import org.briarproject.api.plugins.duplex.DuplexPluginConfig;
import org.briarproject.api.plugins.duplex.DuplexPluginFactory;
import org.briarproject.api.system.LocationUtils;
import org.briarproject.plugins.droidtooth.DroidtoothPluginFactory;
import org.briarproject.plugins.tcp.AndroidLanTcpPluginFactory;
import org.briarproject.plugins.tor.TorPluginFactory;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Executor;

public class PluginsModuleExtension extends PluginsModule {

	private final Application app;

	public PluginsModuleExtension(Application app) {
		this.app = app;
	}

	@Override
	public DuplexPluginConfig provideDuplexPluginConfig(
			@IoExecutor Executor ioExecutor, PlatformExecutor platformExecutor,
			SecureRandom random, BackoffFactory backoffFactory,
			LocationUtils locationUtils, EventBus eventBus) {
		Context appContext = app.getApplicationContext();
		DuplexPluginFactory bluetooth = new DroidtoothPluginFactory(ioExecutor,
				platformExecutor, appContext, random, backoffFactory);
		DuplexPluginFactory tor = new TorPluginFactory(ioExecutor, appContext,
				locationUtils, eventBus);
		DuplexPluginFactory lan = new AndroidLanTcpPluginFactory(ioExecutor,
				backoffFactory, appContext);
		final Collection<DuplexPluginFactory> factories =
				Arrays.asList(bluetooth, tor, lan);
		return new DuplexPluginConfig() {
			public Collection<DuplexPluginFactory> getFactories() {
				return factories;
			}
		};
	}
}
