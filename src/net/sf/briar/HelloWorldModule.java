package net.sf.briar;

import static android.content.Context.MODE_PRIVATE;

import java.io.File;

import net.sf.briar.api.crypto.Password;
import net.sf.briar.api.db.DatabaseConfig;
import net.sf.briar.api.ui.UiCallback;
import android.content.Context;

import com.google.inject.AbstractModule;

public class HelloWorldModule extends AbstractModule {

	private final DatabaseConfig config;
	private final UiCallback callback;

	public HelloWorldModule(final Context appContext) {
		final Password password = new Password() {

			public char[] getPassword() {
				return "foo bar".toCharArray();
			}
		};
		config = new DatabaseConfig() {

			public File getDataDirectory() {
				return appContext.getDir("db", MODE_PRIVATE);
			}

			public Password getPassword() {
				return password;
			}

			public long getMaxSize() {
				return Long.MAX_VALUE;
			}
		};
		callback = new UiCallback() {

			public int showChoice(String[] options, String... message) {
				return -1;
			}

			public boolean showConfirmationMessage(String... message) {
				return false;
			}

			public void showMessage(String... message) {}			
		};
	}

	@Override
	protected void configure() {
		bind(DatabaseConfig.class).toInstance(config);
		bind(UiCallback.class).toInstance(callback);
	}
}
