package org.briarproject.identity;

import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.data.ObjectReader;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.system.Clock;

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
	AuthorFactory provideAuthorFactory(CryptoComponent crypto,
			BdfWriterFactory bdfWriterFactory, Clock clock) {
		return new AuthorFactoryImpl(crypto, bdfWriterFactory, clock);
	}

	@Provides
	@Singleton
	IdentityManager provideIdentityModule(DatabaseComponent db) {
		return new IdentityManagerImpl(db);
	}

	@Provides
	ObjectReader<Author> provideAuthorReader(AuthorFactory authorFactory) {
		return new AuthorReader(authorFactory);
	}
}
