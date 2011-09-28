package net.sf.briar;

import java.io.File;

import net.sf.briar.api.crypto.Password;
import net.sf.briar.api.db.DatabaseDirectory;
import net.sf.briar.api.db.DatabaseMaxSize;
import net.sf.briar.api.db.DatabasePassword;

import com.google.inject.AbstractModule;

public class TestDatabaseModule extends AbstractModule {

	private final File dir;
	private final Password password;

	public TestDatabaseModule(File dir) {
		this.dir = dir;
		this.password = new Password() {
			public char[] getPassword() {
				return "foo bar".toCharArray();
			}
		};
	}

	@Override
	protected void configure() {
		bind(File.class).annotatedWith(DatabaseDirectory.class).toInstance(dir);
		bind(Password.class).annotatedWith(
				DatabasePassword.class).toInstance(password);
		bind(long.class).annotatedWith(
				DatabaseMaxSize.class).toInstance(Long.MAX_VALUE);
	}
}
