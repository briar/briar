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
import java.io.IOException;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static java.nio.file.Files.setPosixFilePermissions;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static java.util.Collections.emptyList;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.reporting.ReportingConstants.DEV_ONION_ADDRESS;
import static org.briarproject.bramble.api.reporting.ReportingConstants.DEV_PUBLIC_KEY_HEX;

@Module
public class HeadlessModule {

	private final static Logger LOG = getLogger(HeadlessModule.class.getName());

	private final String appDir;

	public HeadlessModule() {
		String home = System.getProperty("user.home");
		appDir = home + File.separator + ".briar";
		try {
			ensurePermissions(new File(appDir));
		} catch (IOException e) {
			LOG.log(WARNING, e.getMessage(), e);
		}
	}

	@Provides
	@Singleton
	DatabaseConfig provideDatabaseConfig() {
		File dbDir = appDir("db");
		File keyDir = appDir("key");
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
				return appDir("reportDir");
			}
		};
		return devConfig;
	}

	private File appDir(String file) {
		return new File(appDir + File.separator + file);
	}

	private void ensurePermissions(File file)throws IOException {
		if (!file.exists()) {
			if (!file.mkdirs()) {
				throw new IOException("Could not create directory");
			}
		}
		Set<PosixFilePermission> perms = new HashSet<>();
		perms.add(OWNER_READ);
		perms.add(OWNER_WRITE);
		perms.add(OWNER_EXECUTE);
		setPosixFilePermissions(file.toPath(), perms);
	}

}
