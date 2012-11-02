package net.sf.briar;

import java.io.File;

import net.sf.briar.api.crypto.Password;
import net.sf.briar.api.db.DatabaseConfig;
import net.sf.briar.api.ui.UiCallback;

import com.google.inject.AbstractModule;

public class HelloWorldModule extends AbstractModule {

	private final DatabaseConfig config;
	private final UiCallback callback;

	public HelloWorldModule(final File dir) {
		final Password password = new Password() {

			public char[] getPassword() {
				return "foo bar".toCharArray();
			}
		};
		config = new DatabaseConfig() {

			public File getDataDirectory() {
				return dir;
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
