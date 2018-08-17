package org.briarproject.briar.headless;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.PluginConfig;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginFactory;
import org.briarproject.bramble.api.plugin.simplex.SimplexPluginFactory;
import org.briarproject.bramble.api.reporting.DevConfig;
import org.briarproject.bramble.util.StringUtils;

import java.io.File;
import java.security.GeneralSecurityException;
import java.util.Collection;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static java.util.Collections.emptyList;
import static org.briarproject.bramble.api.reporting.ReportingConstants.DEV_ONION_ADDRESS;
import static org.briarproject.bramble.api.reporting.ReportingConstants.DEV_PUBLIC_KEY_HEX;

@Module
public class HeadlessModule {

	@Provides
	@Singleton
	DatabaseConfig provideDatabaseConfig() {
		File dbDir = new File("dbDir");
		File keyDir = new File("keyDir");
		return new HeadlessDatabaseConfig(dbDir, keyDir);
	}

	@Provides
	PluginConfig providePluginConfig() {
		Collection<DuplexPluginFactory> duplex = emptyList();
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
	DevConfig provideDevConfig(CryptoComponent crypto) {
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
				return new File("reportDir");
			}
		};
		return devConfig;
	}

}
