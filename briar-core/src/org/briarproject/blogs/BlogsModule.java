package org.briarproject.blogs;

import org.briarproject.api.blogs.BlogFactory;
import org.briarproject.api.blogs.BlogManager;
import org.briarproject.api.blogs.BlogPostFactory;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.sync.ValidationManager;
import org.briarproject.api.system.Clock;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class BlogsModule {

	public static class EagerSingletons {
		@Inject
		BlogPostValidator blogPostValidator;
	}

	@Provides
	@Singleton
	BlogManager provideBlogManager(BlogManagerImpl blogManager) {
		return blogManager;
	}

	@Provides
	BlogPostFactory provideBlogPostFactory(CryptoComponent crypto,
			ClientHelper clientHelper) {
		return new BlogPostFactoryImpl(crypto, clientHelper);
	}

	@Provides
	BlogFactory provideBlogFactory(GroupFactory groupFactory,
			AuthorFactory authorFactory, ClientHelper clientHelper) {
		return new BlogFactoryImpl(groupFactory, authorFactory, clientHelper);
	}

	@Provides
	@Singleton
	BlogPostValidator provideBlogPostValidator(
			ValidationManager validationManager, CryptoComponent crypto,
			BlogFactory blogFactory, ClientHelper clientHelper,
			MetadataEncoder metadataEncoder, Clock clock) {

		BlogPostValidator validator = new BlogPostValidator(crypto,
				blogFactory, clientHelper, metadataEncoder, clock);
		validationManager.registerMessageValidator(
				BlogManagerImpl.CLIENT_ID, validator);

		return validator;
	}

}
