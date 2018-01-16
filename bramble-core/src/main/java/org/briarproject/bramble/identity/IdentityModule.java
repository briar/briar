package org.briarproject.bramble.identity;

import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.identity.IdentityManager;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class IdentityModule {

	public static class EagerSingletons {
		@Inject
		IdentityManager identityManager;
	}

	@Provides
	AuthorFactory provideAuthorFactory(AuthorFactoryImpl authorFactory) {
		return authorFactory;
	}

	@Provides
	@Singleton
	IdentityManager provideIdentityManager(
			IdentityManagerImpl identityManager) {
		return identityManager;
	}
}
