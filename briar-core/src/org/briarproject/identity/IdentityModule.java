package org.briarproject.identity;

import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.identity.IdentityManager;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class IdentityModule {

	@Provides
	@Singleton
	IdentityManager provideIdendityModule(DatabaseComponent db, EventBus eventBus) {
		return new IdentityManagerImpl(db, eventBus);
	}
}
