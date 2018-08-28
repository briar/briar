package org.briarproject.briar.headless;

import org.briarproject.bramble.api.ConfigurationManager;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.network.NetworkManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.BackoffFactory;
import org.briarproject.bramble.api.plugin.PluginConfig;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginFactory;
import org.briarproject.bramble.api.plugin.simplex.SimplexPluginFactory;
import org.briarproject.bramble.api.reporting.DevConfig;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.LocationUtils;
import org.briarproject.bramble.api.system.ResourceProvider;
import org.briarproject.bramble.network.JavaNetworkModule;
import org.briarproject.bramble.plugin.tor.CircumventionModule;
import org.briarproject.bramble.plugin.tor.CircumventionProvider;
import org.briarproject.bramble.plugin.tor.LinuxTorPluginFactory;
import org.briarproject.bramble.system.JavaSystemModule;
import org.briarproject.bramble.util.StringUtils;
import org.briarproject.briar.headless.messaging.MessagingModule;

import java.io.File;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.concurrent.Executor;

import javax.inject.Singleton;
import javax.net.SocketFactory;

import dagger.Module;
import dagger.Provides;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.briarproject.bramble.api.reporting.ReportingConstants.DEV_ONION_ADDRESS;
import static org.briarproject.bramble.api.reporting.ReportingConstants.DEV_PUBLIC_KEY_HEX;

@Module(includes = {
		JavaNetworkModule.class,
		JavaSystemModule.class,
		CircumventionModule.class,
		MessagingModule.class
})
public class HeadlessModule {

	@Provides
	@Singleton
	DatabaseConfig provideDatabaseConfig(
			ConfigurationManager configurationManager) {
		File dbDir = appDir(configurationManager, "db");
		File keyDir = appDir(configurationManager, "key");
		return new HeadlessDatabaseConfig(dbDir, keyDir);
	}

	@Provides
	PluginConfig providePluginConfig(@IoExecutor Executor ioExecutor,
			SocketFactory torSocketFactory, BackoffFactory backoffFactory,
			NetworkManager networkManager, LocationUtils locationUtils,
			EventBus eventBus, ResourceProvider resourceProvider,
			CircumventionProvider circumventionProvider, Clock clock,
			ConfigurationManager configurationManager) {
		File torDirectory = appDir(configurationManager, "tor");
		DuplexPluginFactory tor = new LinuxTorPluginFactory(ioExecutor,
				networkManager, locationUtils, eventBus, torSocketFactory,
				backoffFactory, resourceProvider, circumventionProvider, clock,
				torDirectory);
		Collection<DuplexPluginFactory> duplex = singletonList(tor);
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
	DevConfig provideDevConfig(CryptoComponent crypto,
			ConfigurationManager configurationManager) {
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
				return appDir(configurationManager, "reportDir");
			}
		};
		return devConfig;
	}

	@Provides
	@Singleton
	WebSocketController provideWebSocketHandler(
			WebSocketControllerImpl webSocketController) {
		return webSocketController;
	}


	private File appDir(ConfigurationManager configurationManager,
			String file) {
		return new File(configurationManager.getAppDir(), file);
	}

}
