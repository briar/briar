package net.sf.briar.android.helloworld;

import static android.content.Context.MODE_PRIVATE;

import java.io.File;

import net.sf.briar.api.db.DatabaseConfig;
import net.sf.briar.api.ui.UiCallback;
import android.app.Application;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

public class HelloWorldModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(UiCallback.class).toInstance(new UiCallback() {

			public int showChoice(String[] options, String... message) {
				return -1;
			}

			public boolean showConfirmationMessage(String... message) {
				return false;
			}

			public void showMessage(String... message) {}			
		});
	}

	@Provides @Singleton
	DatabaseConfig getDatabaseConfig(final Application app) {
		final File dir = app.getApplicationContext().getDir("db", MODE_PRIVATE);
		return new DatabaseConfig() {

			public boolean databaseExists() {
				return dir.isDirectory() && dir.listFiles().length > 0;
			}

			public File getDatabaseDirectory() {
				return dir;
			}

			public char[] getPassword() {
				return "foo bar".toCharArray();
			}

			public long getMaxSize() {
				return Long.MAX_VALUE;
			}
		};
	}
}
