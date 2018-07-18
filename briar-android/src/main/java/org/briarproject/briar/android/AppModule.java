package org.briarproject.briar.android;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.StrictMode;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.BackoffFactory;
import org.briarproject.bramble.api.plugin.PluginConfig;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginFactory;
import org.briarproject.bramble.api.plugin.simplex.SimplexPluginFactory;
import org.briarproject.bramble.api.reporting.DevConfig;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.LocationUtils;
import org.briarproject.bramble.api.system.Scheduler;
import org.briarproject.bramble.plugin.bluetooth.AndroidBluetoothPluginFactory;
import org.briarproject.bramble.plugin.tcp.AndroidLanTcpPluginFactory;
import org.briarproject.bramble.plugin.tor.CircumventionProvider;
import org.briarproject.bramble.plugin.tor.TorPluginFactory;
import org.briarproject.bramble.util.AndroidUtils;
import org.briarproject.bramble.util.StringUtils;
import org.briarproject.briar.api.android.AndroidNotificationManager;
import org.briarproject.briar.api.android.DozeWatchdog;
import org.briarproject.briar.api.android.ScreenFilterMonitor;

import java.io.File;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.net.SocketFactory;

import dagger.Module;
import dagger.Provides;

import static android.content.Context.MODE_PRIVATE;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.briarproject.bramble.api.reporting.ReportingConstants.DEV_ONION_ADDRESS;
import static org.briarproject.bramble.api.reporting.ReportingConstants.DEV_PUBLIC_KEY_HEX;

@Module
public class AppModule {

	static class EagerSingletons {
		@Inject
		AndroidNotificationManager androidNotificationManager;
		@Inject
		NetworkUsageLogger networkUsageLogger;
		@Inject
		DozeWatchdog dozeWatchdog;
	}

	private final Application application;

	public AppModule(Application application) {
		this.application = application;
	}

	@Provides
	@Singleton
	Application providesApplication() {
		return application;
	}

	@Provides
	@Singleton
	DatabaseConfig provideDatabaseConfig(Application app) {
		//FIXME: StrictMode
		StrictMode.ThreadPolicy tp = StrictMode.allowThreadDiskReads();
		StrictMode.allowThreadDiskWrites();
		File dbDir = app.getApplicationContext().getDir("db", MODE_PRIVATE);
		File keyDir = app.getApplicationContext().getDir("key", MODE_PRIVATE);
		StrictMode.setThreadPolicy(tp);
		@MethodsNotNullByDefault
		@ParametersNotNullByDefault
		DatabaseConfig databaseConfig =
				new AndroidDatabaseConfig(dbDir, keyDir);
		return databaseConfig;
	}

	@Provides
	PluginConfig providePluginConfig(@IoExecutor Executor ioExecutor,
			@Scheduler ScheduledExecutorService scheduler,
			AndroidExecutor androidExecutor, SecureRandom random,
			SocketFactory torSocketFactory, BackoffFactory backoffFactory,
			Application app, LocationUtils locationUtils, EventBus eventBus,
			CircumventionProvider circumventionProvider, Clock clock) {
		Context appContext = app.getApplicationContext();
		DuplexPluginFactory bluetooth =
				new AndroidBluetoothPluginFactory(ioExecutor, androidExecutor,
						appContext, random, eventBus, backoffFactory);
		DuplexPluginFactory tor = new TorPluginFactory(ioExecutor, scheduler,
				appContext, locationUtils, eventBus, torSocketFactory,
				backoffFactory, circumventionProvider, clock);
		DuplexPluginFactory lan = new AndroidLanTcpPluginFactory(ioExecutor,
				scheduler, backoffFactory, appContext);
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

	@Provides
	@Singleton
	DevConfig provideDevConfig(Application app, CryptoComponent crypto) {
		@NotNullByDefault
		DevConfig devConfig = new DevConfig() {

			@Override
			public PublicKey getDevPublicKey() {
				try {
					return crypto.getMessageKeyParser().parsePublicKey(
							StringUtils.fromHexString(DEV_PUBLIC_KEY_HEX));
				} catch (GeneralSecurityException e) {
					throw new RuntimeException(e);
				}
			}

			@Override
			public String getDevOnionAddress() {
				return DEV_ONION_ADDRESS;
			}

			@Override
			public File getReportDir() {
				return AndroidUtils.getReportDir(app.getApplicationContext());
			}
		};
		return devConfig;
	}

	@Provides
	SharedPreferences provideSharedPreferences(Application app) {
		// FIXME unify this with getDefaultSharedPreferences()
		return app.getSharedPreferences("db", MODE_PRIVATE);
	}

	@Provides
	@Singleton
	AndroidNotificationManager provideAndroidNotificationManager(
			LifecycleManager lifecycleManager, EventBus eventBus,
			AndroidNotificationManagerImpl notificationManager) {
		lifecycleManager.registerService(notificationManager);
		eventBus.addListener(notificationManager);
		return notificationManager;
	}

	@Provides
	@Singleton
	ScreenFilterMonitor provideScreenFilterMonitor(
			LifecycleManager lifecycleManager,
			ScreenFilterMonitorImpl screenFilterMonitor) {
		lifecycleManager.registerService(screenFilterMonitor);
		return screenFilterMonitor;
	}

	@Provides
	NetworkUsageLogger provideNetworkUsageLogger(
			LifecycleManager lifecycleManager) {
		NetworkUsageLogger networkUsageLogger = new NetworkUsageLogger();
		lifecycleManager.registerService(networkUsageLogger);
		return networkUsageLogger;
	}

	@Provides
	@Singleton
	DozeWatchdog provideDozeWatchdog(LifecycleManager lifecycleManager) {
		DozeWatchdogImpl dozeWatchdog = new DozeWatchdogImpl(application);
		lifecycleManager.registerService(dozeWatchdog);
		return dozeWatchdog;
	}

}
