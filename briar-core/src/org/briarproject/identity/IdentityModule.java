package org.briarproject.identity;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import org.briarproject.api.data.ObjectReader;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.IdentityManager;

public class IdentityModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(AuthorFactory.class).to(AuthorFactoryImpl.class);
		bind(IdentityManager.class).to(IdentityManagerImpl.class);
	}

	@Provides
	ObjectReader<Author> getAuthorReader(AuthorFactory authorFactory) {
		return new AuthorReader(authorFactory);
	}
}
