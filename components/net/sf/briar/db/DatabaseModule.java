package net.sf.briar.db;

import net.sf.briar.api.crypto.Password;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabasePassword;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class DatabaseModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(Database.class).to(H2Database.class);
		bind(DatabaseComponent.class).to(DatabaseComponentImpl.class).in(
				Singleton.class);
		bind(Password.class).annotatedWith(DatabasePassword.class).toInstance(
				new Password() {
			public char[] getPassword() {
				return "fixme fixme".toCharArray();
			}
		});
	}
}
