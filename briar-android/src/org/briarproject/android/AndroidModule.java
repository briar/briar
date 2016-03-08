package org.briarproject.android;

import android.app.Application;

import org.briarproject.api.android.AndroidExecutor;
import org.briarproject.api.android.AndroidNotificationManager;
import org.briarproject.api.android.ReferenceManager;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DatabaseConfig;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.plugins.PluginManager;
import org.briarproject.api.properties.TransportPropertyManager;
import org.briarproject.api.sync.ValidationManager;
import org.briarproject.api.transport.KeyManager;
import org.briarproject.api.ui.UiCallback;

import java.io.File;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static android.content.Context.MODE_PRIVATE;

@Module
public class AndroidModule {

	static class EagerSingletons {
		// Load all relevant eager singletons and their references
		@Inject
		KeyManager keyManager;
		@Inject
		ValidationManager validationManager;
		@Inject
		PluginManager pluginManager;
		@Inject
		AndroidNotificationManager androidNotificationManager;
		@Inject
		TransportPropertyManager transportPropertyManager;
	}

	static void injectEager(AndroidComponent c) {
		c.inject(new EagerSingletons());
	}

	private final UiCallback uiCallback;

	public AndroidModule() {
		// Use a dummy UI callback
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
	UiCallback provideUICallback() {
		return uiCallback;
	}

	@Provides
	@Singleton
	ReferenceManager provideReferenceManager() {
		return new ReferenceManagerImpl();
	}

	@Provides
	@Singleton
	AndroidExecutor provideAndroidExecutor(
			AndroidExecutorImpl androidExecutor) {
		return androidExecutor;
	}

	@Provides
	@Singleton
	DatabaseConfig provideDatabaseConfig(final Application app) {
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
	AndroidNotificationManager provideAndroidNotificationManager(
			LifecycleManager lifecycleManager, EventBus eventBus,
			AndroidNotificationManagerImpl notificationManager) {
		lifecycleManager.register(notificationManager);
		eventBus.addListener(notificationManager);
		return notificationManager;
	}

}
