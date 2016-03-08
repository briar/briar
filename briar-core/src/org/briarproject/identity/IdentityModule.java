package org.briarproject.identity;

import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.data.ObjectReader;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.system.Clock;
import org.briarproject.crypto.CryptoModule;
import org.briarproject.data.DataModule;
import org.briarproject.db.DatabaseModule;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class IdentityModule {

	@Provides
	AuthorFactory provideAuthorFactory(CryptoComponent crypto,
			BdfWriterFactory bdfWriterFactory, Clock clock) {
		return new AuthorFactoryImpl(crypto, bdfWriterFactory, clock);
	}

	@Provides
	IdentityManager provideIdendityModule(DatabaseComponent db) {
		return new IdentityManagerImpl(db);
	}

	@Provides
	ObjectReader<Author> provideAuthorReader(AuthorFactory authorFactory) {
		return new AuthorReader(authorFactory);
	}
}
