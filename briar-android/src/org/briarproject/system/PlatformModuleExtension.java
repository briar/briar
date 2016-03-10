package org.briarproject.system;

import android.app.Application;
import android.content.Context;

import org.briarproject.PlatformModule;
import org.briarproject.android.ApplicationScope;
import org.briarproject.api.android.PlatformExecutor;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DatabaseConfig;
import org.briarproject.api.ui.UiCallback;

import java.io.File;

public class PlatformModuleExtension extends PlatformModule {

	private final UiCallback uiCallback;
	private final Application app;

	public PlatformModuleExtension(Application app) {
		this.app = app;
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

	@Override
	public UiCallback provideUICallback() {
		return uiCallback;
	}

	@Override
	@ApplicationScope
	public DatabaseConfig provideDatabaseConfig() {
		final File dir = app.getApplicationContext().getDir("db", Context.MODE_PRIVATE);
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

	@Override
	public PlatformExecutor providePlatformExecutor() {
		return new AndroidExecutorImpl(app);
	}
}
