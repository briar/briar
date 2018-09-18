package org.briarproject.briar.android;

import android.app.Application;
import android.content.Context;

import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.network.NetworkManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.BackoffFactory;
import org.briarproject.bramble.api.plugin.PluginConfig;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginFactory;
import org.briarproject.bramble.api.plugin.simplex.SimplexPluginFactory;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.LocationUtils;
import org.briarproject.bramble.api.system.ResourceProvider;
import org.briarproject.bramble.api.system.Scheduler;
import org.briarproject.bramble.plugin.bluetooth.AndroidBluetoothPluginFactory;
import org.briarproject.bramble.plugin.tcp.AndroidLanTcpPluginFactory;
import org.briarproject.bramble.plugin.tor.AndroidTorPluginFactory;
import org.briarproject.bramble.plugin.tor.CircumventionProvider;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import javax.net.SocketFactory;

import dagger.Module;
import dagger.Provides;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

@Module
public class PluginConfigModule {

	@Provides
	PluginConfig providePluginConfig(@IoExecutor Executor ioExecutor,
			@Scheduler ScheduledExecutorService scheduler,
			AndroidExecutor androidExecutor, SecureRandom random,
			SocketFactory torSocketFactory, BackoffFactory backoffFactory,
			Application app, NetworkManager networkManager,
			LocationUtils locationUtils, EventBus eventBus,
			ResourceProvider resourceProvider,
			CircumventionProvider circumventionProvider, Clock clock) {
		Context appContext = app.getApplicationContext();
		DuplexPluginFactory bluetooth =
				new AndroidBluetoothPluginFactory(ioExecutor, androidExecutor,
						appContext, random, eventBus, backoffFactory);
		DuplexPluginFactory tor = new AndroidTorPluginFactory(ioExecutor,
				scheduler, appContext, networkManager, locationUtils, eventBus,
				torSocketFactory, backoffFactory, resourceProvider,
				circumventionProvider, clock);
		DuplexPluginFactory lan = new AndroidLanTcpPluginFactory(ioExecutor,
				eventBus, backoffFactory, appContext);
		Collection<DuplexPluginFactory> duplex = asList(bluetooth, tor, lan);
		@NotNullByDefault
		PluginConfig pluginConfig = new PluginConfig() {

			@Override
			public Collection<DuplexPluginFactory> getDuplexFactories() {
				return duplex;
			}

			@Override
			public Collection<SimplexPluginFactory> getSimplexFactories() {
				return emptyList();
			}

			@Override
			public boolean shouldPoll() {
				return true;
			}
		};
		return pluginConfig;
	}

}
