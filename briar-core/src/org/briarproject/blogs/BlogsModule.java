package org.briarproject.blogs;

import org.briarproject.api.blogs.BlogFactory;
import org.briarproject.api.blogs.BlogManager;
import org.briarproject.api.blogs.BlogPostFactory;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.sync.MessageFactory;
import org.briarproject.api.sync.ValidationManager;
import org.briarproject.api.system.Clock;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static org.briarproject.blogs.BlogManagerImpl.CLIENT_ID;

@Module
public class BlogsModule {

	public static class EagerSingletons {
		@Inject
		BlogPostValidator blogPostValidator;
		@Inject
		BlogManager blogManager;
	}

	@Provides
	@Singleton
	BlogManager provideBlogManager(BlogManagerImpl blogManager,
			LifecycleManager lifecycleManager, ContactManager contactManager,
			IdentityManager identityManager,
			ValidationManager validationManager) {

		lifecycleManager.registerClient(blogManager);
		contactManager.registerAddContactHook(blogManager);
		contactManager.registerRemoveContactHook(blogManager);
		identityManager.registerAddIdentityHook(blogManager);
		identityManager.registerRemoveIdentityHook(blogManager);
		validationManager.registerIncomingMessageHook(CLIENT_ID, blogManager);
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
			GroupFactory groupFactory, MessageFactory messageFactory,
			BlogFactory blogFactory, ClientHelper clientHelper,
			MetadataEncoder metadataEncoder, Clock clock) {

		BlogPostValidator validator = new BlogPostValidator(crypto,
				groupFactory, messageFactory, blogFactory, clientHelper,
				metadataEncoder, clock);
		validationManager.registerMessageValidator(CLIENT_ID, validator);

		return validator;
	}

}
