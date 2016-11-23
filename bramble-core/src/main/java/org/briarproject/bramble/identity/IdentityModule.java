package org.briarproject.bramble.identity;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.data.BdfWriterFactory;
import org.briarproject.bramble.api.data.ObjectReader;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.system.Clock;

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
