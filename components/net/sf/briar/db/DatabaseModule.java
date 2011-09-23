package net.sf.briar.db;

import net.sf.briar.api.db.DatabaseComponent;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class DatabaseModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(Database.class).to(H2Database.class);
		bind(DatabaseComponent.class).to(DatabaseComponentImpl.class).in(
				Singleton.class);
	}
}
