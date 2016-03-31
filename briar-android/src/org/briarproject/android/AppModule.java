package org.briarproject.android;

import android.app.Application;

import org.briarproject.android.api.AndroidNotificationManager;
import org.briarproject.android.api.ReferenceManager;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DatabaseConfig;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.reporting.DevConfig;
import org.briarproject.api.ui.UiCallback;
import org.briarproject.util.StringUtils;

import java.io.File;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static android.content.Context.MODE_PRIVATE;

@Module
public class AppModule {

	static class EagerSingletons {
		@Inject
		AndroidNotificationManager androidNotificationManager;
	}

	private final Application application;
	private final UiCallback uiCallback;

	public AppModule(Application application) {
		this.application = application;
		uiCallback = new UiCallback() {

			public int showChoice(String[] options, String... message) {
				throw new UnsupportedOperationException();
			}

			public boolean showConfirmationMessage(String... message) {
				throw new UnsupportedOperationException();
			}

			public void showMessage(String... message) {
				throw new UnsupportedOperationException();
			}
		};
	}

	@Provides
	@Singleton
	Application providesApplication() {
		return application;
	}

	@Provides
	public UiCallback provideUICallback() {
		return uiCallback;
	}

	@Provides
	@Singleton
	public DatabaseConfig provideDatabaseConfig(Application app) {
		final File dir = app.getApplicationContext().getDir("db", MODE_PRIVATE);
		return new DatabaseConfig() {

			private volatile SecretKey key = null;

			public boolean databaseExists() {
				return dir.isDirectory() && dir.listFiles().length > 0;
			}

			public File getDatabaseDirectory() {
				return dir;
			}

			public void setEncryptionKey(SecretKey key) {
				this.key = key;
			}

			public SecretKey getEncryptionKey() {
				return key;
			}

			public long getMaxSize() {
				return Long.MAX_VALUE;
			}
		};
	}

	@Provides
	@Singleton
	public DevConfig provideDevConfig() {
		return new DevConfig() {

			// TODO fill in
			private final byte[] DEV_PUB_KEY = StringUtils.fromHexString("");
			private final String DEV_ONION = "";
			private final int DEV_REPORT_PORT = 8080;

			@Override
			public byte[] getDevPublicKey() {
				return DEV_PUB_KEY;
			}

			@Override
			public String getDevOnionAddress() {
				return DEV_ONION;
			}

			@Override
			public int getDevReportPort() {
				return DEV_REPORT_PORT;
			}
		};
	}

	@Provides
	@Singleton
	ReferenceManager provideReferenceManager() {
		return new ReferenceManagerImpl();
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
}
